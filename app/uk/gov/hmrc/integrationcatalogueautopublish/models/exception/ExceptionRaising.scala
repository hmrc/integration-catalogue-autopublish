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

import play.api.Logging
import uk.gov.hmrc.http.UpstreamErrorResponse

trait ExceptionRaising {
  self: Logging =>

  object raiseIntegrationCatalogueException {

    def unexpectedResponse(response: UpstreamErrorResponse, context: Seq[(String, AnyRef)]): IntegrationCatalogueException = {
      log(IntegrationCatalogueException.unexpectedResponse(response, context))
    }

    def error(throwable: Throwable, context: Seq[(String, AnyRef)]): IntegrationCatalogueException = {
      log(IntegrationCatalogueException.error(throwable, context))
    }

    def publishError(error: String, context: Seq[(String, AnyRef)]): IntegrationCatalogueException = {
      log(IntegrationCatalogueException.publishError(error, context))
    }

    def missingTeamLink(id: String): IntegrationCatalogueException = {
      log(IntegrationCatalogueException.missingTeamLink(id))
    }
  }

  object raiseOasDiscoveryException {

    def unexpectedResponse(statusCode: Int, context: Seq[(String, AnyRef)]): OasDiscoveryException = {
      log(OasDiscoveryException.unexpectedResponse(statusCode, context))
    }

    def unexpectedResponse(response: UpstreamErrorResponse, context: Seq[(String, AnyRef)]): OasDiscoveryException = {
      log(OasDiscoveryException.unexpectedResponse(response, context))
    }

    def error(throwable: Throwable, context: Seq[(String, AnyRef)]): OasDiscoveryException = {
      log(OasDiscoveryException.error(throwable, context))
    }

  }


  private def log[T <: AutopublishException](e: T): T = {
    logger.warn("Raised application exception", e)
    e
  }

}
