/*
* Copyright 2017 HM Revenue & Customs
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

package repositories

import helpers.SCRSMongoSpec
import repositories.{SubscriptionsMongoRepository, SuccessfulSub, UpsertResult}
import models.Subscription
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.modules.reactivemongo.MongoDbConnection

import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionRepositoryISpec extends SCRSMongoSpec {

  val testValid = sub()

  def construct() =
    Subscription(
      "transId1",
      "test",
      "CT",
      "url"
    )

  def sub(num: Int) = (1 to num).map(n => Subscription(
    s"transId$n",
    s"regime$n",
    s"sub$n",
    s"url$n"
  ))

  def sub() = Subscription(
    "transId1",
    "test",
    "CT",
    "url"
  )

  def subUpdate() = Subscription(
    "transId1",
    "test",
    "CT",
    "newUrl"
  )


  class Setup extends MongoDbConnection {
    val repository = new SubscriptionsMongoRepository(db)
    await(repository.drop)
    await(repository.ensureIndexes)

    def insert(sub: Subscription) = await(repository.insert(sub))
    def count = await(repository.count)
  }

  override def afterAll() = new Setup {
    await(repository.drop)
  }

  val testKey = "testKey"

  "getSubscriptions" should {
    "return a submissions" in new Setup {
      await(repository.count) shouldBe 0
      await(repository.insertSub(testValid))
      await(repository.count) shouldBe 1
      val result = await(repository.getSubscription("transId1"))
      result.head.subscriber shouldBe "CT"
    }
  }

  "insertSub" should {

    "return an Upsert Result" in new Setup {
      val result = await(repository.insertSub(testValid))
      val expected = UpsertResult(0,1,Seq())
      result shouldBe expected

    }

    "update an existing sub that matches the selector" in new Setup {
      insert(sub)
      count shouldBe 1

      val result = await(repository.insertSub(sub))
      count shouldBe 1
      result shouldBe UpsertResult(1,0,Seq())
    }


    "update the callback url when an already existing Subscription is updated with a new call back url" in new Setup {
      val firstResponse = await(repository.insertSub(sub))
      val secondResponse = await(repository.insertSub(subUpdate()))

      firstResponse shouldBe UpsertResult(0,1,Seq())
      secondResponse shouldBe UpsertResult(1,0,Seq())

    }

  }

  "wipeTestData" should {
    "remove all test data from submissions status" in new Setup {
      val result = await(repository.wipeTestData())
      result.hasErrors shouldBe false
    }
  }
}
