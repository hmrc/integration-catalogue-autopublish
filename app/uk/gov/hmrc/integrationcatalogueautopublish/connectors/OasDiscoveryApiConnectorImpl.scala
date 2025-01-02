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
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.{ACCEPT, AUTHORIZATION}
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.integrationcatalogueautopublish.models.ApiDeployment
import uk.gov.hmrc.integrationcatalogueautopublish.models.ApiDeployment._
import uk.gov.hmrc.integrationcatalogueautopublish.models.exception.{ExceptionRaising, OasDiscoveryException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class OasDiscoveryApiConnectorImpl @Inject()(
                                          servicesConfig: ServicesConfig,
                                          httpClient: HttpClientV2
                                        )(implicit ec: ExecutionContext) extends OasDiscoveryApiConnector with Logging with ExceptionRaising with HttpErrorFunctions {

  private val correlationIdHeaderName = "X-Correlation-Id"

  override def allDeployments(correlationId: String)(implicit hc: HeaderCarrier): Future[Either[OasDiscoveryException, Seq[ApiDeployment]]] = {
    val context = Seq(correlationIdHeaderName -> correlationId)

    httpClient.get(url"$baseUrl/v1/oas-deployments")
      .setHeader(ACCEPT -> JSON)
      .setHeader(AUTHORIZATION -> authorization)
      .setHeader(apiKeyHeader*)
      .setHeader(correlationIdHeaderName -> correlationId)
      .withProxy
      .execute[Either[UpstreamErrorResponse, Seq[ApiDeployment]]]
      .map {
        case Right(apiDeployments) =>
          logger.info(s"Retrieved deployments: ${Json.toJson(apiDeployments)}")
          Right(apiDeployments)
        case Left(e) => Left(raiseOasDiscoveryException.unexpectedResponse(e, context))
      }.recover {
      case NonFatal(throwable) => Left(raiseOasDiscoveryException.error(throwable, context))
    }
  }

  override def deployment(correlationId: String, publisherReference: String)(implicit hc: HeaderCarrier): Future[Either[OasDiscoveryException, Option[ApiDeployment]]] = {
    val context = Seq("publisher-reference" -> publisherReference, correlationIdHeaderName -> correlationId)

    httpClient.get(url"$baseUrl/v1/oas-deployments/$publisherReference")
      .setHeader(ACCEPT -> JSON)
      .setHeader(AUTHORIZATION -> authorization)
      .setHeader(apiKeyHeader*)
      .setHeader(correlationIdHeaderName -> correlationId)
      .withProxy
      .execute[Either[UpstreamErrorResponse, ApiDeployment]]
      .map {
        case Right(apiDeployment) =>
          logger.info(s"Retrieved deployment: ${Json.toJson(apiDeployment)}")
          Right(Some(apiDeployment))
        case Left(e) if e.statusCode == 404 => Right(None)
        case Left(e) => Left(raiseOasDiscoveryException.unexpectedResponse(e, context))
      }.recover {
        case NonFatal(throwable) => Left(raiseOasDiscoveryException.error(throwable, context))
      }
  }

  override def oas(id: String, correlationId: String)(implicit hc: HeaderCarrier): Future[Either[OasDiscoveryException, String]] = {
    val context = Seq(
      "id" -> id,
      correlationIdHeaderName -> correlationId
    )

    httpClient.get(url"$baseUrl/v1/oas-deployments/$id/oas")
      .setHeader(ACCEPT -> "application/yaml")
      .setHeader(AUTHORIZATION -> authorization)
      .setHeader(apiKeyHeader*)
      .setHeader(correlationIdHeaderName -> correlationId)
      .withProxy
      .execute[HttpResponse]
      .map(
        response =>
          if (is2xx(response.status)) {
            logger.info(s"Retrieved oas for id $id: ${response.body}")
            Right(response.body)
          }
          else {
            Left(raiseOasDiscoveryException.unexpectedResponse(response.status, context))
          }
      )
      .recover {
        case NonFatal(throwable) => Left(raiseOasDiscoveryException.error(throwable, context))
      }
  }

  private def apiKeyHeader: Seq[(String, String)] = {
    servicesConfig.getConfString("oas-discovery-api.api-key", "not configured") match {
      case "not configured" => Seq.empty
      case key => Seq(("x-api-key", key))
      }
  }

  private def baseUrl: String = {
    val baseUrl = servicesConfig.baseUrl("oas-discovery-api")
    val path = servicesConfig.getConfString("oas-discovery-api.path", "")

    if (path.isEmpty) {
      baseUrl
    }
    else {
      s"$baseUrl/$path"
    }
  }

  private def authorization: String = {
    val clientId = servicesConfig.getConfString("oas-discovery-api.clientId", "")
    val secret = servicesConfig.getConfString("oas-discovery-api.secret", "")

    s"Basic ${Base64.getEncoder.encodeToString(s"$clientId:$secret".getBytes("UTF-8"))}"
  }

}
