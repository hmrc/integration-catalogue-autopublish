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

package uk.gov.hmrc.integrationcatalogueautopublish.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.EitherValues
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.integrationcatalogueautopublish.models.ApiDeployment
import uk.gov.hmrc.integrationcatalogueautopublish.models.exception.{OasDiscoveryCallError, OasDiscoveryException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import java.util.{Base64, UUID}

class OasDiscoveryApiConnectorSpec
  extends AsyncFreeSpec
  with Matchers
  with WireMockSupport
  with HttpClientV2Support
  with EitherValues {

  import OasDiscoveryApiConnectorSpec._

  "allDeployments" - {
    "must place the correct request to Oas Discovery Api and return deployments" in {
      stubFor(
        get(urlEqualTo("/v1/oas-deployments"))
          .withHeader("Accept", equalTo("application/json"))
          .withHeader("Authorization", equalTo(oasDiscoveryAuth))
          .willReturn(
            aResponse()
              .withBody(Json.toJson(someDeployments).toString())
          )
      )

      buildConnector().allDeployments()(HeaderCarrier()).map {
        result =>
          result mustBe Right(someDeployments)
      }
    }

    "must set the api key header when it is defined in config" in {
      stubFor(
        get(urlEqualTo("/v1/oas-deployments"))
          .withHeader("Accept", equalTo("application/json"))
          .withHeader("Authorization", equalTo(oasDiscoveryAuth))
          .withHeader("x-api-key", equalTo(apiKey))
          .willReturn(
            aResponse()
              .withBody(Json.toJson(someDeployments).toString())
          )
      )

      buildConnector(Some(apiKey)).allDeployments()(HeaderCarrier()).map {
        result =>
          result mustBe Right(someDeployments)
      }
    }

    "must return the correct OasDiscoveryException when an unexpected response is received" in {
      stubFor(
        get(urlEqualTo("/v1/oas-deployments"))
          .willReturn(
            aResponse()
              .withStatus(400)
          )
      )

      buildConnector().allDeployments()(HeaderCarrier()).map {
        result =>
          result mustBe Left(OasDiscoveryException.unexpectedResponse(400))
      }
    }

    "must return the correct OasDiscoveryException when an unexpected error occurs" in {
      stubFor(
        get(urlEqualTo("/v1/oas-deployments"))
          .willReturn(
            aResponse()
              .withFault(Fault.CONNECTION_RESET_BY_PEER)
          )
      )

      buildConnector().allDeployments()(HeaderCarrier()).map {
        result =>
          result.isLeft mustBe true
          result.left.value.issue mustBe OasDiscoveryCallError
      }
    }
  }

  "oas" - {
    "must place the correct request to Oas Discovery Api and return the OAS document" in {
      stubFor(
        get(urlEqualTo(s"/v1/oas-deployments/$id/oas"))
          .withHeader("Accept", equalTo("application/yaml"))
          .withHeader("Authorization", equalTo(oasDiscoveryAuth))
          .willReturn(
            aResponse()
              .withBody(oas)
          )
      )

      buildConnector().oas(id)(HeaderCarrier()).map {
        result =>
          result mustBe Right(oas)
      }
    }

    "must set the api key header when it is defined in config" in {
      stubFor(
        get(urlEqualTo(s"/v1/oas-deployments/$id/oas"))
          .withHeader("Accept", equalTo("application/yaml"))
          .withHeader("Authorization", equalTo(oasDiscoveryAuth))
          .withHeader("x-api-key", equalTo(apiKey))
          .willReturn(
            aResponse()
              .withBody(oas)
          )
      )

      buildConnector(Some(apiKey)).oas(id)(HeaderCarrier()).map {
        result =>
          result mustBe Right(oas)
      }
    }

    "must return the correct OasDiscoveryException when an unexpected response is received" in {
      stubFor(
        get(urlEqualTo(s"/v1/oas-deployments/$id/oas"))
          .willReturn(
            aResponse()
              .withStatus(400)
          )
      )

      buildConnector().oas(id)(HeaderCarrier()).map {
        result =>
          result mustBe Left(OasDiscoveryException.unexpectedResponse(400))
      }
    }

    "must return the correct OasDiscoveryException when an unexpected error occurs" in {
      stubFor(
        get(urlEqualTo(s"/v1/oas-deployments/$id/oas"))
          .willReturn(
            aResponse()
              .withFault(Fault.CONNECTION_RESET_BY_PEER)
          )
      )

      buildConnector().oas(id)(HeaderCarrier()).map {
        result =>
          result.isLeft mustBe true
          result.left.value.issue mustBe OasDiscoveryCallError
      }
    }
  }

  private def buildConnector(maybeApiKey: Option[String] = None): OasDiscoveryApiConnector = {
    var configMap: Map[String,Any] = Map(
      "microservice.services.oas-discovery.host" -> wireMockHost,
      "microservice.services.oas-discovery.port" -> wireMockPort,
      "internal-auth.token" -> internalAuthToken,
      "microservice.services.oas-discovery.clientId" -> testClientId,
      "microservice.services.oas-discovery.secret" -> testSecret,
      "appName" -> "test-app-name"
    )

    if (maybeApiKey.isDefined) {
      configMap = configMap + ("microservice.services.oas-discovery.api-key" -> maybeApiKey.get)
    }

    val configuration = Configuration.from(configMap)

    val servicesConfig = new ServicesConfig(configuration)

    new OasDiscoveryApiConnectorImpl(servicesConfig, httpClientV2)
  }

}

object OasDiscoveryApiConnectorSpec {

  private val internalAuthToken = "test-internal-auth-token"
  private val apiKey = "test-api-key-for-ebridge"
  private val id = "test-id"
  private val oas = "test-oas"
  private val testClientId = "test-client-id"
  private val testSecret = "test-secret"
  private val oasDiscoveryAuth = s"Basic ${Base64.getEncoder.encodeToString(s"$testClientId:$testSecret".getBytes("UTF-8"))}"
  private val deploymentId1 = UUID.randomUUID().toString
  private val deploymentId2 = UUID.randomUUID().toString
  private val someDeployments = Seq(ApiDeployment(deploymentId1, Instant.now()), ApiDeployment(deploymentId2, Instant.now()))

}
