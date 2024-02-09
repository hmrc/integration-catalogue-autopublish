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

package uk.gov.hmrc.integrationcatalogueautopublish.repositories

import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.integrationcatalogueautopublish.models.Api
import uk.gov.hmrc.integrationcatalogueautopublish.utils.MdcTesting
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.http.logging.Mdc

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class ApiRepositorySpec
  extends AnyFreeSpec
  with Matchers
  with DefaultPlayMongoRepositorySupport[Api]
  with OptionValues
  with MdcTesting {

  import ApiRepositorySpec._

  private lazy val playApplication = {
    new GuiceApplicationBuilder()
      .overrides(
        bind[MongoComponent].toInstance(mongoComponent)
      )
      .build()
  }

  private implicit lazy val executionContext: ExecutionContext = {
    playApplication.injector.instanceOf[ExecutionContext]
  }

  override protected lazy val repository: ApiRepository = {
    playApplication.injector.instanceOf[ApiRepository]
  }

  "upsert" - {
    "must insert an API when it does not already exist" in {
      setMdcData()

      val latest = for {
        saved <- repository.upsert(newApi)
        latest <- findById(saved.id.value)
      } yield latest

      val result = latest
        .map(ResultWithMdcData(_))
        .futureValue

      result.data.value.publisherReference mustBe publishReference
      result.data.value.deploymentTimestamp mustBe deploymentTimestamp
      result.mdcData mustBe testMdcData
    }

    "must update an API when it already exists" in {
      val updatedTimestamp = deploymentTimestamp.plusSeconds(1)

      val latest = for {
        saved <- insertApi(newApi)
        updated <- repository.upsert(saved.copy(deploymentTimestamp = updatedTimestamp))
        latest <- findById(updated.id.value)
      } yield latest

      val result = latest
        .map(ResultWithMdcData(_))
        .futureValue

      result.data.value.deploymentTimestamp mustBe updatedTimestamp
      result.mdcData mustBe testMdcData
    }
  }

  "findByPublisherReference" - {
    "must return the Api when it exists" in {
      val found = for {
        _ <- insertApi(newApi)
        found <- repository.findByPublisherReference(publishReference)
      } yield found

      val result = found
        .map(ResultWithMdcData(_))
        .futureValue

      result.data.value.publisherReference mustBe publishReference
      result.mdcData mustBe testMdcData
    }

    "must return None when the Api dos not exist" in {
      val result = repository
        .findByPublisherReference(publishReference)
        .map(ResultWithMdcData(_))
        .futureValue

      result.data mustBe None
      result.mdcData mustBe testMdcData
    }
  }

  private def findById(id: String): Future[Option[Api]] = {
    Mdc.preservingMdc {
      find(Filters.equal("_id", new ObjectId(id)))
        .map(_.headOption)
    }
  }

  private def insertApi(api: Api): Future[Api] = {
    Mdc.preservingMdc {
      insert(api)
        .map(
          result =>
            api.copy(
              id = Some(result.getInsertedId.asObjectId().getValue.toString)
            )
        )
    }
  }

}

object ApiRepositorySpec {

  private val publishReference: String = "test-publisher-reference"
  private val deploymentTimestamp: Instant = Instant.now()
  private val newApi: Api = Api(publishReference, deploymentTimestamp)

}
