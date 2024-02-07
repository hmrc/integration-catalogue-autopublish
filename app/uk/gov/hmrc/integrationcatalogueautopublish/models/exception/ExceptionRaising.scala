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

    def unexpectedResponse(response: UpstreamErrorResponse): IntegrationCatalogueException = {
      log(IntegrationCatalogueException.unexpectedResponse(response))
    }

    def error(throwable: Throwable): IntegrationCatalogueException = {
      log(IntegrationCatalogueException.error(throwable))
    }

    def publishError(error: String): IntegrationCatalogueException = {
      log(IntegrationCatalogueException.publishError(error))
    }

  }

  private def log[T <: AutopublishException](e: T): T = {
    logger.warn("Raised application exception", e)
    e
  }

}
