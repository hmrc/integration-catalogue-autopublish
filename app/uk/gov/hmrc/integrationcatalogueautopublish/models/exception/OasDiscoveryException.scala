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

sealed trait OasDiscoveryIssue

case object OasDiscoveryUnexpectedResponse extends OasDiscoveryIssue
case object OasDiscoveryCallError extends OasDiscoveryIssue


case class OasDiscoveryException(message: String, cause: Throwable, issue: OasDiscoveryIssue) extends AutopublishException(message, cause)

object OasDiscoveryException {

  def apply(message: String, issue: OasDiscoveryIssue): OasDiscoveryException = {
    OasDiscoveryException(message, null, issue)
  }

  def unexpectedResponse(statusCode: Int): OasDiscoveryException = {
    OasDiscoveryException(s"Unexpected response $statusCode returned from Integration Catalogue", OasDiscoveryUnexpectedResponse)
  }

  def unexpectedResponse(response: UpstreamErrorResponse): OasDiscoveryException = {
    unexpectedResponse(response.statusCode)
  }

  def error(throwable: Throwable): OasDiscoveryException = {
    OasDiscoveryException("Error calling Integration Catalogue", throwable, OasDiscoveryCallError)
  }

}
