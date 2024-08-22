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

package uk.gov.hmrc.integrationcatalogueautopublish.repositories.models

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{Format, Json};

class MongoIdentifierSpec extends AnyFreeSpec with Matchers {

  private case class WithMongoIdentifier(id: Option[String], foo: String) extends MongoIdentifier

  private implicit val formatter: Format[WithMongoIdentifier] =
    MongoIdentifier.formatDataWithMongoIdentifier(Json.format[WithMongoIdentifier])

  "MongoIdentifier" - {
    "must wirete a mongo document with an optional identifier" - {
      "when the id exists" in {
        val id = "id"
        val document = WithMongoIdentifier(Some(id), "bar")
        val expectedJson = s"""
                              |{
                              |  "foo": "${document.foo}",
                              |  "_id": { "$$oid": "$id" }
                              |}""".stripMargin.filterNot(_.isWhitespace)

        Json.stringify(Json.toJson(document)) must be(expectedJson)
      }
      "when the id does not exist" in {
        val document = WithMongoIdentifier(None, "bar")
        val expectedJson = s"""
                      |{
                      |  "foo": "${document.foo}"
                      |}""".stripMargin.filterNot(_.isWhitespace)

        Json.stringify(Json.toJson(document)) must be(expectedJson)
      }
    }
    "must read a mongo document with an optional identifier" - {
      "when the id exists" in {
        val id = "id"
        val expectedResult = WithMongoIdentifier(Some("id"), "bar")
        val json = s"""
          |{
          |  "_id": { "$$oid": "$id" },
          |  "foo": "${expectedResult.foo}"
          |}
          """.stripMargin

        Json.parse(json).as[WithMongoIdentifier] must be(expectedResult)
      }
    }
  }

}