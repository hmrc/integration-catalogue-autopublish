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

import com.google.inject.{Inject, Singleton}
import com.mongodb.client.model.IndexOptions
import org.mongodb.scala.model.{Filters, IndexModel, Indexes, ReplaceOptions}
import org.mongodb.scala.SingleObservableFuture
import uk.gov.hmrc.integrationcatalogueautopublish.models.Api
import uk.gov.hmrc.integrationcatalogueautopublish.repositories.models.MongoIdentifier._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApiRepository @Inject()(mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Api](
    collectionName = "apis",
    domainFormat = formatDataWithMongoIdentifier[Api],
    mongoComponent = mongoComponent,
    indexes = Seq(
      IndexModel(Indexes.ascending("publisherReference"), new IndexOptions().unique(true))
    )
  ) {

  override lazy val requiresTtlIndex = false

  def upsert(api: Api): Future[Api] = {
    Mdc.preservingMdc {
      collection
        .replaceOne(
          filter = Filters.equal("publisherReference", api.publisherReference),
          replacement = api,
          options = ReplaceOptions().upsert(true)
        )
        .toFuture()
    } map {
      updateResult =>
        if (updateResult.getModifiedCount == 0) {
          api.copy(id = Some(updateResult.getUpsertedId.asObjectId().getValue.toString))
        }
        else {
          api
        }
    }
  }

  def findByPublisherReference(publisherReference: String): Future[Option[Api]] = {
    Mdc.preservingMdc {
      collection
        .find(Filters.equal("publisherReference", publisherReference))
        .headOption()
    }
  }

}
