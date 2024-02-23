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
import org.mockito.MockitoSugar
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.integrationcatalogueautopublish.connectors.{IntegrationCatalogueConnector, OasDiscoveryApiConnector}
import uk.gov.hmrc.integrationcatalogueautopublish.models.{Api, ApiDeployment}
import uk.gov.hmrc.integrationcatalogueautopublish.models.exception.{ExceptionRaising, OasDiscoveryException}
import uk.gov.hmrc.integrationcatalogueautopublish.repositories.ApiRepository

import java.time.Instant
import scala.concurrent.Future

class AutopublishServiceSpec extends AsyncFreeSpec with Matchers with MockitoSugar {

  "autopublish" - {
    "must handle failure of call to fetch all deployments" in {
      val fixture = buildFixture()
      val exception = OasDiscoveryException.unexpectedResponse(500)
      when(fixture.oasDiscoveryApiConnector.allDeployments()(any)).thenReturn(Future.successful(Left(exception)))
      fixture.autopublishService.autopublish()(new HeaderCarrier()) map(result => result must be(Right(())))
    }

    "must handle no deployments" in {
      val fixture = buildFixture()
      when(fixture.oasDiscoveryApiConnector.allDeployments()(any)).thenReturn(Future.successful(Right(Seq.empty)))
      fixture.autopublishService.autopublish()(new HeaderCarrier()) map (result => result must be(Right(())))
    }

    "must do nothing when the deployments are unchanged" in {
      val fixture = buildFixture()
      val now = Instant.now
      when(fixture.oasDiscoveryApiConnector.allDeployments()(any)).thenReturn(Future.successful(Right(Seq(ApiDeployment("test-id", now)))))
      when(fixture.apiRepository.findByPublisherReference("test-id")).thenReturn(Future.successful(Some(Api(Some("id"), "test-id", now))))
      verifyZeroInteractions(fixture.oasDiscoveryApiConnector.oas(any)(any))
      verifyZeroInteractions(fixture.integrationCatalogueConnector.publishApi(any,any)(any))
      fixture.autopublishService.autopublish()(new HeaderCarrier()) map (result => result must be(Right(())))
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

