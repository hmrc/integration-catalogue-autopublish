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

package uk.gov.hmrc.integrationcatalogueautopublish.models.exception

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{description, never, verify, when}
import org.scalatest.freespec.{AnyFreeSpec, AsyncFreeSpec}
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.integrationcatalogueautopublish.connectors.{IntegrationCatalogueConnector, OasDiscoveryApiConnector, OasDiscoveryApiConnectorImpl}
import uk.gov.hmrc.integrationcatalogueautopublish.models.exception.{IntegrationCatalogueException, OasDiscoveryException}
import uk.gov.hmrc.integrationcatalogueautopublish.models.{Api, ApiDeployment}
import uk.gov.hmrc.integrationcatalogueautopublish.repositories.ApiRepository

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

class OasDiscoveryExceptionSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  "OasDiscoveryException" - {
    "unexpectedResponse must contain the correct details for status code" in {
      val exception = OasDiscoveryException.unexpectedResponse(404, Seq(("key", "value")))
      exception.message mustBe "Unexpected response 404 returned from OAS Discovery API key=value"
      exception.issue mustBe OasDiscoveryUnexpectedResponse
    }

    "unexpectedResponse must contain the correct details for UpstreamErrorResponse" in {
      val upstreamErrorResponse = UpstreamErrorResponse("test", 404, 404, Map.empty)
      val exception = OasDiscoveryException.unexpectedResponse(upstreamErrorResponse, Seq(("key", "value")))
      exception.message mustBe "Unexpected response 404 returned from OAS Discovery API key=value"
      exception.issue mustBe OasDiscoveryUnexpectedResponse
    }

    "error must contain the correct details" in {
      val cause = new RuntimeException("test")
      val exception = OasDiscoveryException.error(cause, Seq(("key", "value")))
      exception.message mustBe "Error calling OAS Discovery API key=value"
      exception.cause mustBe cause
      exception.issue mustBe OasDiscoveryCallError
    }

    "oasNotFound must contain the correct details" in {
      val exception = OasDiscoveryException.oasNotFound("serviceId")
      exception.message mustBe "OAS not found for service serviceId"
      exception.issue mustBe OasNotFound
    }

    "deploymentNotFound must contain the correct details" in {
      val exception = OasDiscoveryException.deploymentNotFound("serviceId")
      exception.message mustBe "Deployment not found for service serviceId"
      exception.issue mustBe OasDeploymentNotFound
    }

    "noDeploymentTimestamp must contain the correct details" in {
      val exception = OasDiscoveryException.noDeploymentTimestamp("serviceId")
      exception.message mustBe "No deployment timestamp for service serviceId"
      exception.issue mustBe OasNoDeploymentTimestamp
    }
  }

}
