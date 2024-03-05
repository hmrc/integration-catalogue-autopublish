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

package uk.gov.hmrc.integrationcatalogueautopublish.tasks

import com.google.inject.{Inject, Singleton}
import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.integrationcatalogueautopublish.config.AppConfig
import uk.gov.hmrc.integrationcatalogueautopublish.services.AutopublishService

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.control.NonFatal

@Singleton
class AutopublishTask @Inject()(
  actorSystem: ActorSystem,
  appConfig: AppConfig,
  autopublishService: AutopublishService
)(implicit ec: ExecutionContext) extends Logging {

  if (appConfig.autopublishTaskInitialDelay.length > 0 && appConfig.autopublishTaskInterval.length > 0) {
    logger.info("Configuring autopublish schedule")

    actorSystem.scheduler.scheduleAtFixedRate(
      initialDelay = toFiniteDuration(appConfig.autopublishTaskInitialDelay),
      interval = toFiniteDuration(appConfig.autopublishTaskInterval),
    ) {
      () =>
        try {
          autopublishService.autopublish()(HeaderCarrier())
        }
        catch {
          case NonFatal(e) => logger.error("Exception thrown by autopublish service", e)
        }
    }
  }
  else {
    logger.info("No autopublish schedule defined")
  }

  private def toFiniteDuration(duration: Duration): FiniteDuration = {
    FiniteDuration(duration.length, duration.unit)
  }

}
