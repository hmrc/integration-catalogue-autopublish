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

package uk.gov.hmrc.integrationcatalogueautopublish.config

import play.api.{Configuration, Environment}
import play.api.inject.Binding
import uk.gov.hmrc.integrationcatalogueautopublish.tasks.AutopublishTask

import scala.collection.immutable.Seq

class Module extends play.api.inject.Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] = {
    Seq(
      bind[AppConfig].toSelf.eagerly(),
      bind[AutopublishTask].toSelf.eagerly(),
      authTokenInitialiserBinding(configuration)
    )
  }

  private def authTokenInitialiserBinding(configuration: Configuration): Binding[?] = {
    if (configuration.get[Boolean]("create-internal-auth-token-on-start")) {
      bind[InternalAuthTokenInitialiser].to[InternalAuthTokenInitialiserImpl].eagerly()
    }
    else {
      bind[InternalAuthTokenInitialiser].to[NoOpInternalAuthTokenInitialiser].eagerly()
    }
  }

}
