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
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.integrationcatalogueautopublish.config.AppConfig
import uk.gov.hmrc.integrationcatalogueautopublish.models.ApiDeployment._
import uk.gov.hmrc.integrationcatalogueautopublish.models.exception.{ExceptionRaising, OasDiscoveryException}
import uk.gov.hmrc.integrationcatalogueautopublish.models.{ApiDeployment, OasDocument}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
@Singleton
class OasDiscoveryApiConnectorImpl @Inject()(
                                          servicesConfig: ServicesConfig,
                                          httpClient: HttpClientV2,
                                          appConfig: AppConfig
                                        )(implicit ec: ExecutionContext) extends OasDiscoveryApiConnector with Logging with ExceptionRaising {

  override def allDeployments()(implicit hc: HeaderCarrier): Future[Either[OasDiscoveryException, Seq[ApiDeployment]]] = {
    httpClient.get(url"$baseUrl/v1/oas-deployments")
      .setHeader(ACCEPT -> JSON)
      .setHeader(AUTHORIZATION -> authorization)
      .setHeader(apiKeyHeader: _*)
      .withProxy
      .execute[Either[UpstreamErrorResponse, Seq[ApiDeployment]]]
      .map {
        case Right(apiDeployments) => Right(apiDeployments)
        case Left(e) => Left(raiseOasDiscoveryException.unexpectedResponse(e))
      }.recover {
      case NonFatal(throwable) => Left(raiseOasDiscoveryException.error(throwable))
    }
  }

  override def oas(id: String)(implicit hc: HeaderCarrier): Future[Either[OasDiscoveryException, OasDocument]] = {
    httpClient.get(url"$baseUrl/v1/oas-deployments/$id/oas")
      .setHeader(ACCEPT -> JSON)
      .setHeader(AUTHORIZATION -> authorization)
      .setHeader(apiKeyHeader: _*)
      .withProxy
      .execute[Either[UpstreamErrorResponse, OasDocument]]
      .map {
        case Right(apiDeployments) => Right(apiDeployments)
        case Left(e) => Left(raiseOasDiscoveryException.unexpectedResponse(e))
      }.recover {
      case NonFatal(throwable) => Left(raiseOasDiscoveryException.error(throwable))
    }

  }

  private def apiKeyHeader: Seq[(String, String)] = {
    servicesConfig.getConfString("oas-discovery.api-key", "not configured") match {
      case "not configured" => Seq.empty
      case key => Seq(("x-api-key", key))
      }
  }

  private def baseUrl: String = {
    val baseUrl = servicesConfig.baseUrl("oas-discovery")
    val path = servicesConfig.getConfString("oas-discovery.path", "")

    if (path.isEmpty) {
      baseUrl
    }
    else {
      s"$baseUrl/$path"
    }
  }

  private def authorization: String = {
    val clientId = servicesConfig.getConfString("oas-discovery.clientId", "")
    val secret = servicesConfig.getConfString("oas-discovery.secret", "")

    s"Basic ${Base64.getEncoder.encodeToString(s"$clientId:$secret".getBytes("UTF-8"))}"
  }

}
