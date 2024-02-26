/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.integrationcatalogueautopublish.services
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.integrationcatalogueautopublish.connectors.{IntegrationCatalogueConnector, OasDiscoveryApiConnector}
import uk.gov.hmrc.integrationcatalogueautopublish.models.exception.{IntegrationCatalogueException, OasDiscoveryException}
import uk.gov.hmrc.integrationcatalogueautopublish.models.{Api, ApiDeployment, OasDocument}
import uk.gov.hmrc.integrationcatalogueautopublish.repositories.ApiRepository

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.Future



class AutopublishServiceSpec extends AsyncFreeSpec with Matchers with MockitoSugar {

  val mongoId = "mongo-id"
  val testId = "test-id"

  "autopublish" - {
    "must handle failure of call to fetch all deployments" in {
      val fixture = buildFixture()
      val exception = OasDiscoveryException.unexpectedResponse(500)
      when(fixture.oasDiscoveryApiConnector.allDeployments()(any)).thenReturn(Future.successful(Left(exception)))
      fixture.autopublishService.autopublish()(new HeaderCarrier()) map(result => {
        verifyZeroInteractions(fixture.oasDiscoveryApiConnector.oas(any)(any))
        verifyZeroInteractions(fixture.integrationCatalogueConnector.publishApi(any, any)(any))
        verifyZeroInteractions(fixture.apiRepository.upsert(any))
        result must be(Right(()))
      })
    }

    "must handle no deployments" in {
      val fixture = buildFixture()
      when(fixture.oasDiscoveryApiConnector.allDeployments()(any)).thenReturn(Future.successful(Right(Seq.empty)))
      fixture.autopublishService.autopublish()(new HeaderCarrier()) map (result => {
        verifyZeroInteractions(fixture.oasDiscoveryApiConnector.oas(any)(any))
        verifyZeroInteractions(fixture.integrationCatalogueConnector.publishApi(any, any)(any))
        verifyZeroInteractions(fixture.apiRepository.upsert(any))
        result must be(Right(()))
      })
    }

    "must do nothing when the deployments are unchanged" in {
      val fixture = buildFixture()
      val now = Instant.now
      when(fixture.oasDiscoveryApiConnector.allDeployments()(any)).thenReturn(Future.successful(Right(Seq(ApiDeployment(testId, now)))))
      when(fixture.apiRepository.findByPublisherReference(testId)).thenReturn(Future.successful(Some(Api(Some(mongoId), testId, now))))
      fixture.autopublishService.autopublish()(new HeaderCarrier()) map (result => {
        verifyZeroInteractions(fixture.oasDiscoveryApiConnector.oas(any)(any))
        verifyZeroInteractions(fixture.integrationCatalogueConnector.publishApi(any, any)(any))
        verifyZeroInteractions(fixture.apiRepository.upsert(any))
        result must be(Right(()))
      })
    }

    "must publish new oas file and update repository when a deployment has changed" in {
      val fixture = buildFixture()
      val now = Instant.now
      val earlier = now.minus(1, ChronoUnit.DAYS)
      when(fixture.oasDiscoveryApiConnector.allDeployments()(any)).thenReturn(Future.successful(Right(Seq(ApiDeployment(testId, now)))))
      val api = Api(Some(mongoId), testId, earlier)
      when(fixture.apiRepository.findByPublisherReference(testId)).thenReturn(Future.successful(Some(api)))
      when(fixture.oasDiscoveryApiConnector.oas(ArgumentMatchers.eq(testId))(any)).thenReturn(Future.successful(Right(OasDocument(testId,"some oas"))))
      when(fixture.apiRepository.upsert(api)).thenReturn(Future.successful(api.copy(deploymentTimestamp = now)))
      when(fixture.integrationCatalogueConnector.publishApi(ArgumentMatchers.eq(testId), ArgumentMatchers.eq("some oas"))(any)).thenReturn(Future.successful(Right(())))
      fixture.autopublishService.autopublish()(new HeaderCarrier()) map (result => {
        verify(fixture.integrationCatalogueConnector).publishApi(ArgumentMatchers.eq(testId),ArgumentMatchers.eq( "some oas"))(any)
        verify(fixture.apiRepository).upsert(api)
        result must be(Right(()))
      })
    }

    "must not update repository when a deployment has changed but publish fails" in {
      val fixture = buildFixture()
      val now = Instant.now
      val earlier = now.minus(1, ChronoUnit.DAYS)
      when(fixture.oasDiscoveryApiConnector.allDeployments()(any)).thenReturn(Future.successful(Right(Seq(ApiDeployment(testId, now)))))
      val api = Api(Some(mongoId), testId, earlier)
      when(fixture.apiRepository.findByPublisherReference(testId)).thenReturn(Future.successful(Some(api)))
      when(fixture.oasDiscoveryApiConnector.oas(ArgumentMatchers.eq(testId))(any)).thenReturn(Future.successful(Right(OasDocument(testId, "some oas"))))
      when(fixture.apiRepository.upsert(api)).thenReturn(Future.successful(api.copy(deploymentTimestamp = now)))
      when(fixture.integrationCatalogueConnector.publishApi(ArgumentMatchers.eq(testId), ArgumentMatchers.eq("some oas"))(any)).thenReturn(Future.successful(Left(IntegrationCatalogueException.unexpectedResponse(500))))
      fixture.autopublishService.autopublish()(new HeaderCarrier()) map (result => {
        verify(fixture.integrationCatalogueConnector).publishApi(ArgumentMatchers.eq(testId), ArgumentMatchers.eq("some oas"))(any)
        verifyZeroInteractions(fixture.apiRepository.upsert(any))
        result must be(Right(()))
      })
    }
  }

  private case class Fixture( apiRepository: ApiRepository,
                              integrationCatalogueConnector: IntegrationCatalogueConnector,
                              oasDiscoveryApiConnector: OasDiscoveryApiConnector,
                              autopublishService: AutopublishService)

  private def buildFixture(): Fixture = {
    val apiRepository = mock[ApiRepository]
    val integrationCatalogueConnector = mock[IntegrationCatalogueConnector]
    val oasDiscoveryApiConnector = mock[OasDiscoveryApiConnector]
    val autopublishService = new AutopublishService(oasDiscoveryApiConnector, integrationCatalogueConnector, apiRepository)
    Fixture(apiRepository, integrationCatalogueConnector, oasDiscoveryApiConnector, autopublishService)
  }
}
