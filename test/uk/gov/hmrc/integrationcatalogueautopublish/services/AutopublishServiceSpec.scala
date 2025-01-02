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
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.integrationcatalogueautopublish.connectors.{IntegrationCatalogueConnector, OasDiscoveryApiConnector, OasDiscoveryApiConnectorImpl}
import uk.gov.hmrc.integrationcatalogueautopublish.models.exception.{IntegrationCatalogueException, OasDiscoveryException}
import uk.gov.hmrc.integrationcatalogueautopublish.models.{Api, ApiDeployment}
import uk.gov.hmrc.integrationcatalogueautopublish.repositories.ApiRepository

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

class AutopublishServiceSpec extends AsyncFreeSpec with Matchers with MockitoSugar {

  private val mongoId = "mongo-id"
  private val testId = "test-id"
  private val correlationId = "test-correlation-id"
  private val publisherReference = "publisher-reference"

  "autopublish" - {
    "must handle failure of call to fetch all deployments" in {
      val fixture = buildFixture()
      val exception = OasDiscoveryException.unexpectedResponse(500, Seq.empty)

      when(fixture.oasDiscoveryApiConnector.allDeployments(eqTo(correlationId))(any)).thenReturn(Future.successful(Left(exception)))

      fixture.autopublishService.autopublish()(new HeaderCarrier()).map(result => {
        verify(fixture.oasDiscoveryApiConnector, never()).oas(any, any)(any)
        verify(fixture.integrationCatalogueConnector, never()).publishApi(any, any, any)(any)
        verify(fixture.apiRepository, never()).upsert(any)
        result must be(Left(exception))
      })
    }

    "must handle no deployments" in {
      val fixture = buildFixture()
      when(fixture.oasDiscoveryApiConnector.allDeployments(eqTo(correlationId))(any)).thenReturn(Future.successful(Right(Seq.empty)))
      fixture.autopublishService.autopublish()(new HeaderCarrier()).map (result => {
        verify(fixture.oasDiscoveryApiConnector, never()).oas(any, any)(any)
        verify(fixture.integrationCatalogueConnector, never()).publishApi(any, any, any)(any)
        verify(fixture.apiRepository, never()).upsert(any)
        result must be(Right(()))
      })
    }

    "must do nothing when the deployments are unchanged" in {
      val fixture = buildFixture()
      val now = Instant.now
      when(fixture.oasDiscoveryApiConnector.allDeployments(eqTo(correlationId))(any)).thenReturn(Future.successful(Right(Seq(ApiDeployment(testId, Some(now))))))
      when(fixture.apiRepository.findByPublisherReference(testId)).thenReturn(Future.successful(Some(Api(Some(mongoId), testId, now))))
      fixture.autopublishService.autopublish()(new HeaderCarrier()).map (result => {
        verify(fixture.oasDiscoveryApiConnector, never()).oas(any, any)(any)
        verify(fixture.integrationCatalogueConnector, never()).publishApi(any, any, any)(any)
        verify(fixture.apiRepository, never()).upsert(any)
        result must be(Right(()))
      })
    }

    "must publish new oas file and update repository when a deployment has changed" in {
      val fixture = buildFixture()
      val now = Instant.now
      val earlier = now.minus(1, ChronoUnit.DAYS)
      val deployment = ApiDeployment(testId, Some(now))

      when(fixture.oasDiscoveryApiConnector.allDeployments(eqTo(correlationId))(any)).thenReturn(Future.successful(Right(Seq(deployment))))

      val api = Api(Some(mongoId), testId, earlier)
      when(fixture.apiRepository.findByPublisherReference(testId)).thenReturn(Future.successful(Some(api)))

      when(fixture.oasDiscoveryApiConnector.oas(eqTo(testId), eqTo(correlationId))(any)).thenReturn(Future.successful(Right("some oas")))

      val updatedApi = api.copy(deploymentTimestamp = now)
      when(fixture.apiRepository.upsert(updatedApi)).thenReturn(Future.successful(updatedApi))
      when(fixture.integrationCatalogueConnector.publishApi(eqTo(testId), eqTo("some oas"), eqTo(correlationId))(any)).thenReturn(Future.successful(Right(())))

      fixture.autopublishService.autopublish()(new HeaderCarrier()).map (result => {
        verify(fixture.integrationCatalogueConnector).publishApi(eqTo(testId), eqTo( "some oas"), eqTo(correlationId))(any)
        verify(fixture.apiRepository).upsert(updatedApi)
        result must be(Right(()))
      })
    }

    "must still update repository when a deployment has changed but no team link record was found" in {
      val fixture = buildFixture()
      val now = Instant.now
      val earlier = now.minus(1, ChronoUnit.DAYS)

      val deployment = ApiDeployment(testId, Some(now))
      when(fixture.oasDiscoveryApiConnector.allDeployments(eqTo(correlationId))(any)).thenReturn(Future.successful(Right(Seq(deployment))))

      val api = Api(Some(mongoId), testId, earlier)
      when(fixture.apiRepository.findByPublisherReference(testId)).thenReturn(Future.successful(Some(api)))
      when(fixture.oasDiscoveryApiConnector.oas(eqTo(testId), eqTo(correlationId))(any)).thenReturn(Future.successful(Right("some oas")))

      val updatedApi = api.copy(deploymentTimestamp = now)
      when(fixture.apiRepository.upsert(updatedApi)).thenReturn(Future.successful(updatedApi))
      when(fixture.integrationCatalogueConnector.publishApi(eqTo(testId), eqTo("some oas"), eqTo(correlationId))(any))
        .thenReturn(Future.successful(Left(IntegrationCatalogueException.missingTeamLink(testId))))

      fixture.autopublishService.autopublish()(new HeaderCarrier()).map (result => {
        verify(fixture.apiRepository).upsert(updatedApi)
        result must be(Right(()))
      })
    }

    "must not update repository when a deployment has changed but publish fails" in {
      val fixture = buildFixture()
      val now = Instant.now
      val earlier = now.minus(1, ChronoUnit.DAYS)
      val oas = "some oas"
      val context = Seq("id" -> testId, "oas" -> oas)

      when(fixture.oasDiscoveryApiConnector.allDeployments(eqTo(correlationId))(any)).thenReturn(Future.successful(Right(Seq(ApiDeployment(testId, Some(now))))))

      val api = Api(Some(mongoId), testId, earlier)
      when(fixture.apiRepository.findByPublisherReference(testId)).thenReturn(Future.successful(Some(api)))
      when(fixture.oasDiscoveryApiConnector.oas(eqTo(testId), eqTo(correlationId))(any)).thenReturn(Future.successful(Right(oas)))
      when(fixture.apiRepository.upsert(api)).thenReturn(Future.successful(api.copy(deploymentTimestamp = now)))
      when(fixture.integrationCatalogueConnector.publishApi(eqTo(testId), eqTo(oas), eqTo(correlationId))(any)).thenReturn(Future.successful(Left(IntegrationCatalogueException.unexpectedResponse(500, context))))

      fixture.autopublishService.autopublish()(new HeaderCarrier()).map (result => {
        verify(fixture.integrationCatalogueConnector).publishApi(eqTo(testId), eqTo("some oas"), eqTo(correlationId))(any)
        verify(fixture.apiRepository, never()).upsert(any)
        result must be(Right(()))
      })
    }

    "must handle failure to update repo" in {
      val fixture = buildFixture()
      val now = Instant.now
      val earlier = now.minus(1, ChronoUnit.DAYS)
      val deployment = ApiDeployment(testId, Some(now))
      when(fixture.oasDiscoveryApiConnector.allDeployments(eqTo(correlationId))(any)).thenReturn(Future.successful(Right(Seq(deployment))))

      val api = Api(Some(mongoId), testId, earlier)
      when(fixture.apiRepository.findByPublisherReference(any)).thenReturn(Future.successful(Some(api)))
      when(fixture.oasDiscoveryApiConnector.oas(any, any)(any)).thenReturn(Future.successful(Right("some oas")))

      val updatedApi = api.copy(deploymentTimestamp = now)
      when(fixture.apiRepository.upsert(updatedApi)).thenThrow(new RuntimeException("bang"))
      when(fixture.integrationCatalogueConnector.publishApi(any, any, any)(any)).thenReturn(Future.successful(Right(())))

      fixture.autopublishService.autopublish()(new HeaderCarrier()).map (result => {
        verify(fixture.apiRepository).upsert(updatedApi)
        result must be(Right(()))
      })
    }

    "must ignore deployments without a timestamp" in {
      val fixture = buildFixture()
      val deployments = Seq(ApiDeployment("test-id", None))
      when(fixture.oasDiscoveryApiConnector.allDeployments(eqTo(correlationId))(any)).thenReturn(Future.successful(Right(deployments)))
      fixture.autopublishService.autopublish()(new HeaderCarrier()).map (result => {
        verify(fixture.apiRepository, never()).findByPublisherReference(any)
        result must be(Right(()))
      })
    }
  }

  "autopublishOne" - {
    "must handle failure of call to fetch a deployment" in {
      val fixture = buildFixture()
      val exception = OasDiscoveryException.unexpectedResponse(500, Seq.empty)

      when(fixture.oasDiscoveryApiConnector.deployment(eqTo(correlationId), eqTo(publisherReference))(any)).thenReturn(Future.successful(Left(exception)))

      fixture.autopublishService.autopublishOne(publisherReference)(new HeaderCarrier()).map(result => {
        verify(fixture.oasDiscoveryApiConnector, never()).oas(any, any)(any)
        verify(fixture.integrationCatalogueConnector, never()).publishApi(any, any, any)(any)
        verify(fixture.apiRepository, never()).upsert(any)
        result must be(Left(exception))
      })
    }

    "must handle no deployment" in {
      val fixture = buildFixture()
      when(fixture.oasDiscoveryApiConnector.deployment(eqTo(correlationId), eqTo(publisherReference))(any)).thenReturn(Future.successful(Right(None)))
      fixture.autopublishService.autopublishOne(publisherReference)(new HeaderCarrier()).map(result => {
        verify(fixture.oasDiscoveryApiConnector, never()).oas(any, any)(any)
        verify(fixture.integrationCatalogueConnector, never()).publishApi(any, any, any)(any)
        verify(fixture.apiRepository, never()).upsert(any)
        result must be(Right(()))
      })
    }

    "must publish new oas file and update repository when a deployment has changed" in {
      val fixture = buildFixture()
      val now = Instant.now
      val earlier = now.minus(1, ChronoUnit.DAYS)
      val deployment = ApiDeployment(testId, Some(now))

      when(fixture.oasDiscoveryApiConnector.deployment(eqTo(correlationId), eqTo(publisherReference))(any)).thenReturn(Future.successful(Right(Some(deployment))))

      val api = Api(Some(mongoId), testId, earlier)
      when(fixture.apiRepository.findByPublisherReference(testId)).thenReturn(Future.successful(Some(api)))

      when(fixture.oasDiscoveryApiConnector.oas(eqTo(testId), eqTo(correlationId))(any)).thenReturn(Future.successful(Right("some oas")))

      val updatedApi = api.copy(deploymentTimestamp = now)
      when(fixture.apiRepository.upsert(updatedApi)).thenReturn(Future.successful(updatedApi))
      when(fixture.integrationCatalogueConnector.publishApi(eqTo(testId), eqTo("some oas"), eqTo(correlationId))(any)).thenReturn(Future.successful(Right(())))

      fixture.autopublishService.autopublishOne(publisherReference)(new HeaderCarrier()).map(result => {
        verify(fixture.integrationCatalogueConnector).publishApi(eqTo(testId), eqTo("some oas"), eqTo(correlationId))(any)
        verify(fixture.apiRepository).upsert(updatedApi)
        result must be(Right(()))
      })
    }

    "must still update repository when a deployment has changed but no team link record was found" in {
      val fixture = buildFixture()
      val now = Instant.now
      val earlier = now.minus(1, ChronoUnit.DAYS)

      val deployment = ApiDeployment(testId, Some(now))
      when(fixture.oasDiscoveryApiConnector.deployment(eqTo(correlationId), eqTo(publisherReference))(any)).thenReturn(Future.successful(Right(Some(deployment))))

      val api = Api(Some(mongoId), testId, earlier)
      when(fixture.apiRepository.findByPublisherReference(testId)).thenReturn(Future.successful(Some(api)))
      when(fixture.oasDiscoveryApiConnector.oas(eqTo(testId), eqTo(correlationId))(any)).thenReturn(Future.successful(Right("some oas")))

      val updatedApi = api.copy(deploymentTimestamp = now)
      when(fixture.apiRepository.upsert(updatedApi)).thenReturn(Future.successful(updatedApi))
      when(fixture.integrationCatalogueConnector.publishApi(eqTo(testId), eqTo("some oas"), eqTo(correlationId))(any))
        .thenReturn(Future.successful(Left(IntegrationCatalogueException.missingTeamLink(testId))))

      fixture.autopublishService.autopublishOne(publisherReference)(new HeaderCarrier()).map(result => {
        verify(fixture.apiRepository).upsert(updatedApi)
        result must be(Right(()))
      })
    }

    "must not update repository when a deployment has changed but publish fails" in {
      val fixture = buildFixture()
      val now = Instant.now
      val earlier = now.minus(1, ChronoUnit.DAYS)
      val oas = "some oas"
      val context = Seq("id" -> testId, "oas" -> oas)

      when(fixture.oasDiscoveryApiConnector.deployment(eqTo(correlationId), eqTo(publisherReference))(any)).thenReturn(Future.successful(Right(Some(ApiDeployment(testId, Some(now))))))

      val api = Api(Some(mongoId), testId, earlier)
      when(fixture.apiRepository.findByPublisherReference(testId)).thenReturn(Future.successful(Some(api)))
      when(fixture.oasDiscoveryApiConnector.oas(eqTo(testId), eqTo(correlationId))(any)).thenReturn(Future.successful(Right(oas)))
      when(fixture.apiRepository.upsert(api)).thenReturn(Future.successful(api.copy(deploymentTimestamp = now)))
      when(fixture.integrationCatalogueConnector.publishApi(eqTo(testId), eqTo(oas), eqTo(correlationId))(any)).thenReturn(Future.successful(Left(IntegrationCatalogueException.unexpectedResponse(500, context))))

      fixture.autopublishService.autopublishOne(publisherReference)(new HeaderCarrier()).map(result => {
        verify(fixture.integrationCatalogueConnector).publishApi(eqTo(testId), eqTo("some oas"), eqTo(correlationId))(any)
        verify(fixture.apiRepository, never()).upsert(any)
        result must be(Right(()))
      })
    }

    "must handle failure to update repo" in {
      val fixture = buildFixture()
      val now = Instant.now
      val earlier = now.minus(1, ChronoUnit.DAYS)
      val deployment = ApiDeployment(testId, Some(now))
      when(fixture.oasDiscoveryApiConnector.deployment(eqTo(correlationId), eqTo(publisherReference))(any)).thenReturn(Future.successful(Right(Some(deployment))))

      val api = Api(Some(mongoId), testId, earlier)
      when(fixture.apiRepository.findByPublisherReference(any)).thenReturn(Future.successful(Some(api)))
      when(fixture.oasDiscoveryApiConnector.oas(any, any)(any)).thenReturn(Future.successful(Right("some oas")))

      val updatedApi = api.copy(deploymentTimestamp = now)
      when(fixture.apiRepository.upsert(updatedApi)).thenThrow(new RuntimeException("bang"))
      when(fixture.integrationCatalogueConnector.publishApi(any, any, any)(any)).thenReturn(Future.successful(Right(())))

      fixture.autopublishService.autopublishOne(publisherReference)(new HeaderCarrier()).map(result => {
        verify(fixture.apiRepository).upsert(updatedApi)
        result must be(Right(()))
      })
    }

    "must ignore a deployment without a timestamp" in {
      val fixture = buildFixture()
      val deployments = Seq(ApiDeployment("test-id", None))
      when(fixture.oasDiscoveryApiConnector.deployment(eqTo(correlationId), eqTo(publisherReference))(any)).thenReturn(Future.successful(Right(Some(deployments.head))))
      fixture.autopublishService.autopublishOne(publisherReference)(new HeaderCarrier()).map(result => {
        verify(fixture.apiRepository, never()).findByPublisherReference(any)
        result must be(Right(()))
      })
    }
  }

  private case class Fixture(
    apiRepository: ApiRepository,
    integrationCatalogueConnector: IntegrationCatalogueConnector,
    oasDiscoveryApiConnector: OasDiscoveryApiConnector,
    autopublishService: AutopublishService
  )

  private def buildFixture(): Fixture = {
    val apiRepository = mock[ApiRepository]
    val integrationCatalogueConnector = mock[IntegrationCatalogueConnector]
    val oasDiscoveryApiConnector = mock[OasDiscoveryApiConnectorImpl]

    val correlationIdProvider = mock[CorrelationIdProvider]
    when(correlationIdProvider.provide()).thenReturn(correlationId)

    val autopublishService = new AutopublishService(oasDiscoveryApiConnector, integrationCatalogueConnector, apiRepository, correlationIdProvider)
    Fixture(apiRepository, integrationCatalogueConnector, oasDiscoveryApiConnector, autopublishService)
  }
}
