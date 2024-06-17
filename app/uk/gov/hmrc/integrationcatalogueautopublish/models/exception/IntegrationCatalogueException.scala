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

import uk.gov.hmrc.http.UpstreamErrorResponse

sealed trait IntegrationCatalogueIssue

case object UnexpectedResponse extends IntegrationCatalogueIssue
case object CallError extends IntegrationCatalogueIssue
case object PublishError extends IntegrationCatalogueIssue
case object MissingTeamLink extends IntegrationCatalogueIssue

case class IntegrationCatalogueException(message: String, cause: Throwable, issue: IntegrationCatalogueIssue) extends AutopublishException(message, cause)

object IntegrationCatalogueException {

  def apply(message: String, issue: IntegrationCatalogueIssue): IntegrationCatalogueException = {
    IntegrationCatalogueException(message, null, issue)
  }

  def unexpectedResponse(statusCode: Int): IntegrationCatalogueException = {
    IntegrationCatalogueException(s"Unexpected response $statusCode returned from Integration Catalogue", UnexpectedResponse)
  }

  def unexpectedResponse(response: UpstreamErrorResponse): IntegrationCatalogueException = {
    unexpectedResponse(response.statusCode)
  }

  def error(throwable: Throwable): IntegrationCatalogueException = {
    IntegrationCatalogueException("Error calling Integration Catalogue", throwable, CallError)
  }

  def publishError(error: String): IntegrationCatalogueException = {
    IntegrationCatalogueException(s"Publish error: $error", PublishError)
  }

  def missingTeamLink(id: String): IntegrationCatalogueException = {
    IntegrationCatalogueException(s"Missing team link for API $id", MissingTeamLink)
  }

}
