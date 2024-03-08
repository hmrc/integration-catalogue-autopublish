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

package uk.gov.hmrc.integrationcatalogueautopublish.services

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.integrationcatalogueautopublish.connectors.{IntegrationCatalogueConnector, OasDiscoveryApiConnectorImpl}
import uk.gov.hmrc.integrationcatalogueautopublish.models.Api
import uk.gov.hmrc.integrationcatalogueautopublish.models.exception.AutopublishException
import uk.gov.hmrc.integrationcatalogueautopublish.repositories.ApiRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AutopublishService @Inject()(oasDiscoveryConnector: OasDiscoveryApiConnectorImpl,
                                   integrationCatalogueConnector: IntegrationCatalogueConnector,
                                   apiRepository: ApiRepository)(implicit ec: ExecutionContext) extends Logging {

  def autopublish()(implicit hc: HeaderCarrier): Future[Either[AutopublishException, Unit]] = {
    logger.info("Starting auto-publish")
    oasDiscoveryConnector.allDeployments() flatMap {
      case Right(deployments) =>
        Future.sequence(deployments.map(deployment => {
          deployment.deploymentTimestamp match {
            case Some(deploymentTimestamp) =>
              apiRepository.findByPublisherReference(deployment.id) flatMap {
                case Some(api) if api.deploymentTimestamp.isBefore(deploymentTimestamp) =>
                  publishAndUpsertRepository(api.copy(deploymentTimestamp = deploymentTimestamp))
                case None =>
                  publishAndUpsertRepository(Api(deployment.id, deploymentTimestamp))
                case _ => Future.successful(Right(()))
              }
            case _ =>
              Future.successful(Right(()))
          }
        })).flatMap(_ => Future.successful(Right(())))

      case Left(e) => Future.successful(Left(e))
    }
  }

  private def publishAndUpsertRepository(api: Api)(implicit hc: HeaderCarrier): Future[Either[AutopublishException, Unit]] = {
    oasDiscoveryConnector.oas(api.publisherReference) flatMap {
      case Right(oasDocument) => integrationCatalogueConnector.publishApi(api.publisherReference, oasDocument) flatMap {
        case Right(()) =>
          try {
            apiRepository.upsert(api).flatMap(_ => Future.successful(Right(())))
          } catch {
            case e: Throwable =>
              logger.error(s"Failed to upsert repo: ${e.getMessage}")
              Future.successful(Right(()))
          }
        case Left(e) => Future.successful(Left(e))
      }
      case Left(e) => Future.successful(Left(e))
    }
  }

}
