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
import uk.gov.hmrc.integrationcatalogueautopublish.config.AppConfig
import uk.gov.hmrc.integrationcatalogueautopublish.connectors.IntegrationCatalogueConnector.{PublishError, PublishRequest, PublishResult}
import uk.gov.hmrc.integrationcatalogueautopublish.models.exception.{CallError, IntegrationCatalogueException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class IntegrationCatalogueConnectorSpec
  extends AsyncFreeSpec
  with Matchers
  with WireMockSupport
  with HttpClientV2Support
  with EitherValues {

  import IntegrationCatalogueConnectorSpec._

  "publishApi" - {
    "must place the correct request to Integration Catalogue and return Unit on success" in {
      stubFor(
        put(urlEqualTo(s"/integration-catalogue/apis/publish"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withHeader("Accept", equalTo("application/json"))
          .withHeader("Authorization", equalTo(internalAuthToken))
          .withRequestBody(
            equalToJson(Json.toJson(publishRequest).toString())
          )
          .willReturn(
            aResponse()
              .withBody(Json.toJson(buildPublishResult(isSuccess = true)).toString())
          )
      )

      buildConnector().publishApi(id, oas)(HeaderCarrier()).map {
        result =>
          result mustBe Right(())
      }
    }

    "must return the correct IntegrationCatalogueException when the publish request is unsuccessful" in {
      val publishResult = buildPublishResult(isSuccess = false, errors)

      stubFor(
        put(urlEqualTo(s"/integration-catalogue/apis/publish"))
          .willReturn(
            aResponse()
              .withBody(Json.toJson(publishResult).toString())
          )
      )

      buildConnector().publishApi(id, oas)(HeaderCarrier()).map {
        result =>
          result mustBe Left(IntegrationCatalogueException.publishError(publishResult.listErrors()))
      }
    }

    "must return the correct IntegrationCatalogueException when an unexpected response is received" in {
      stubFor(
        put(urlEqualTo(s"/integration-catalogue/apis/publish"))
          .willReturn(
            aResponse()
              .withStatus(400)
          )
      )

      buildConnector().publishApi(id, oas)(HeaderCarrier()).map {
        result =>
          result mustBe Left(IntegrationCatalogueException.unexpectedResponse(400))
      }
    }

    "must return the correct IntegrationCatalogueException when an unexpected error occurs" in {
      stubFor(
        put(urlEqualTo(s"/integration-catalogue/apis/publish"))
          .willReturn(
            aResponse()
              .withFault(Fault.CONNECTION_RESET_BY_PEER)
          )
      )

      buildConnector().publishApi(id, oas)(HeaderCarrier()).map {
        result =>
          result.isLeft mustBe true
          result.left.value.issue mustBe CallError
      }
    }
  }

  private def buildConnector(): IntegrationCatalogueConnector = {
    val configuration = Configuration.from(
      Map(
        "microservice.services.integration-catalogue.host" -> wireMockHost,
        "microservice.services.integration-catalogue.port" -> wireMockPort,
        "internal-auth.token" -> internalAuthToken,
        "appName" -> "test-app-name",
        "tasks.autopublish.initialDelay" -> "1 hour",
        "tasks.autopublish.interval" -> "1 hour"
      )
    )

    val servicesConfig = new ServicesConfig(configuration)
    val appConfig = new AppConfig(configuration)

    new IntegrationCatalogueConnector(servicesConfig, httpClientV2, appConfig)
  }

}

object IntegrationCatalogueConnectorSpec {

  private val internalAuthToken = "test-internal-auth-token"
  private val id = "test-id"
  private val oas = "test-oas"

  private val publishRequest = PublishRequest(Some(id), "HIP", "OAS_V3", oas)

  private val errors = Seq(
    PublishError(101, "test-error-101"),
    PublishError(201, "test-error-201")
  )

  private def buildPublishResult(isSuccess: Boolean, errors: Seq[PublishError] = Seq.empty): PublishResult = {
    PublishResult(isSuccess, errors)
  }

}
