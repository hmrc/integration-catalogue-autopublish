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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{verify, when}
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.integrationcatalogueautopublish.models.exception.{AutopublishException, OasDiscoveryException}
import uk.gov.hmrc.integrationcatalogueautopublish.services.AutopublishService

import scala.concurrent.Future

class AutopublishControllerSpec
  extends AnyFreeSpec
  with Matchers
  with MockitoSugar
  with OptionValues {

  "autopublishNow" - {
    "must call the service layer and return 204 No Content on success" in {
      val fixture = buildFixture()

      when(fixture.autopublishService.autopublish()(any)).thenReturn(Future.successful(Right(())))

      running(fixture.application) {
        val request = FakeRequest(routes.AutopublishController.autopublishNow())
        val result = route(fixture.application, request).value

        status(result) mustBe NO_CONTENT
        verify(fixture.autopublishService).autopublish()(any)
      }
    }

    "must throw any exception returned by the service layer" in {
      val fixture = buildFixture()
      val ex = new AutopublishException("test-message", null) {}

      when(fixture.autopublishService.autopublish()(any)).thenReturn(Future.successful(Left(ex)))

      running(fixture.application) {
        val request = FakeRequest(routes.AutopublishController.autopublishNow())
        val result = route(fixture.application, request).value

        the [AutopublishException] thrownBy await(result) mustBe ex
      }
    }
  }

  "autopublishOne" - {
    val publisherReference = "publisher-reference"
    "must call the service layer and return 204 No Content on success" in {
      val fixture = buildFixture()

      when(fixture.autopublishService.autopublishOne(eqTo(publisherReference))(any)).thenReturn(Future.successful(Right(())))

      running(fixture.application) {
        val request = FakeRequest(routes.AutopublishController.autopublishOne(publisherReference))
        val result = route(fixture.application, request).value

        status(result) mustBe NO_CONTENT
        verify(fixture.autopublishService).autopublishOne(eqTo(publisherReference))(any)
      }
    }

    "must return 404 Not Found if the deployment cannot be found" in {
      val fixture = buildFixture()
      val exception = OasDiscoveryException.deploymentNotFound(publisherReference)

      when(fixture.autopublishService.autopublishOne(eqTo(publisherReference))(any))
        .thenReturn(Future.successful(Left(exception)))

      running(fixture.application) {
        val request = FakeRequest(routes.AutopublishController.autopublishOne(publisherReference))
        val result = route(fixture.application, request).value

        status(result) mustBe NOT_FOUND
      }
    }

    "must return 404 Not Found if the OAS cannot be found" in {
      val fixture = buildFixture()
      val exception = OasDiscoveryException.oasNotFound(publisherReference)

      when(fixture.autopublishService.autopublishOne(eqTo(publisherReference))(any))
        .thenReturn(Future.successful(Left(exception)))

      running(fixture.application) {
        val request = FakeRequest(routes.AutopublishController.autopublishOne(publisherReference))
        val result = route(fixture.application, request).value

        status(result) mustBe NOT_FOUND
      }
    }

    "must throw any exception returned by the service layer" in {
      val fixture = buildFixture()
      val ex = new AutopublishException("test-message", null) {}

      when(fixture.autopublishService.autopublishOne(eqTo(publisherReference))(any)).thenReturn(Future.successful(Left(ex)))

      running(fixture.application) {
        val request = FakeRequest(routes.AutopublishController.autopublishOne(publisherReference))
        val result = route(fixture.application, request).value

        the [AutopublishException] thrownBy await(result) mustBe ex
      }
    }
  }

  private case class Fixture(application: Application, autopublishService: AutopublishService)

  private def buildFixture(): Fixture = {
    val autopublishService = mock[AutopublishService]

    val application = new GuiceApplicationBuilder()
      .overrides(
        bind[ControllerComponents].toInstance(Helpers.stubControllerComponents()),
        bind[AutopublishService].toInstance(autopublishService)
      )
      .build()

    Fixture(application, autopublishService)
  }

}
