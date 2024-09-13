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

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.http.HeaderNames._
import play.api.http.MimeTypes.JSON
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.integrationcatalogueautopublish.config.AppConfig
import uk.gov.hmrc.integrationcatalogueautopublish.models.exception.{ExceptionRaising, IntegrationCatalogueException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class IntegrationCatalogueConnector @Inject()(
                                               servicesConfig: ServicesConfig,
                                               httpClient: HttpClientV2,
                                               appConfig: AppConfig
                                             )(implicit ec: ExecutionContext) extends Logging with ExceptionRaising {

  import IntegrationCatalogueConnector._

  private val integrationCatalogueBaseUrl = servicesConfig.baseUrl("integration-catalogue")
  private val clientAuthToken = appConfig.internalAuthToken
  private val correlationIdHeaderName = "X-Correlation-Id"

  def publishApi(id: String, oas: String, correlationId: String)(implicit hc: HeaderCarrier): Future[Either[IntegrationCatalogueException, Unit]] = {
    val context = Seq("id" -> id, "oas" -> oas, correlationIdHeaderName -> correlationId)

    httpClient.put(url"$integrationCatalogueBaseUrl/integration-catalogue/apis/publish")
      .setHeader(CONTENT_TYPE -> JSON)
      .setHeader(ACCEPT -> JSON)
      .setHeader(AUTHORIZATION -> clientAuthToken)
      .setHeader(correlationIdHeaderName -> correlationId)
      .withBody(Json.toJson(PublishRequest(id, oas)))
      .execute[Either[UpstreamErrorResponse, PublishResult]]
      .map {
        case Right(publishResult) =>
          logger.info(s"Publish result: ${Json.toJson(publishResult)}")

          publishResult match {
            case PublishResult(true, _) =>
              Right(())
            case PublishResult(false, errors) if errors.exists(_.code.equals(ErrorCodes.MISSING_TEAM_LINK)) =>
              Left(raiseIntegrationCatalogueException.missingTeamLink(id))
            case _ =>
              Left(raiseIntegrationCatalogueException.publishError(publishResult.listErrors(), context))
          }
        case Left(e) => Left(raiseIntegrationCatalogueException.unexpectedResponse(e, context))
      }.recover {
      case NonFatal(throwable) => Left(raiseIntegrationCatalogueException.error(throwable, context))
    }
  }

}

object IntegrationCatalogueConnector {

  case class PublishRequest(publisherReference: Option[String], platformType: String, specificationType: String, contents: String, autopublish: Boolean)

  object PublishRequest {

    val DEFAULT_PLATFORM_TYPE: String = "HIP"
    val DEFAULT_SPECIFICATION_TYPE: String = "OAS_V3"

    def apply(publisherReference: String, contents: String): PublishRequest = {
      PublishRequest(Some(publisherReference), DEFAULT_PLATFORM_TYPE, DEFAULT_SPECIFICATION_TYPE, contents, autopublish = true)
    }

    implicit val formatPublishRequest: Format[PublishRequest] = Json.format[PublishRequest]

  }

  case class PublishError(code: Int, message: String)

  object PublishError {

    implicit val formatPublishError: Format[PublishError] = Json.format[PublishError]

  }

  case class PublishResult(isSuccess: Boolean, errors: Seq[PublishError] = Seq.empty) {

    def listErrors(): String = {
      errors
        .map(error => s"${error.code}: ${error.message}")
        .mkString(System.lineSeparator())
    }

  }

  object PublishResult {

    implicit val formatPublishResult: Format[PublishResult] = Json.format[PublishResult]

  }

  object ErrorCodes {

    val OAS_PARSE_ERROR  = 1000
    val API_UPSERT_ERROR = 1001
    val MISSING_TEAM_LINK = 1002

  }

}
