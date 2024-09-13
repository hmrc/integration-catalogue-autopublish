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

package uk.gov.hmrc.integrationcatalogueautopublish.controllers

import com.google.inject.Inject
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request}
import uk.gov.hmrc.integrationcatalogueautopublish.services.{AutopublishService, CorrelationIdProvider}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

class AutopublishController @Inject()(
  cc: ControllerComponents,
  autopublishService: AutopublishService
)(implicit ec: ExecutionContext) extends BackendController(cc) {

  def autopublishNow(): Action[AnyContent] = Action.async {
    implicit request: Request[AnyContent] =>
      autopublishService.autopublish().map {
        case Right(_) => NoContent
        case Left(e) => throw e
      }
  }

}
