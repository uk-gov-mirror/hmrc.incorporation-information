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
import models.Subscription
import play.modules.reactivemongo.MongoDbConnection

import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionRepositoryISpec extends SCRSMongoSpec {

  val testValid = sub()

  def sub(): Subscription = sub(1).head
  def sub(num: Int) = subDetail("transId", "regime", "sub", "url")(num)
  def secondSub() = subDetail("transId", "test", "PAYE", "url")(1).head

  def subDetail(t: String, r: String, s: String, url: String)(num: Int) = (1 to num).map(n => Subscription(
    s"${t}${n}",
    s"${r}${n}",
    s"${s}",
    s"${url}${n}"
  ))

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

  "getSubscriptions" should {
    "return a submissions" in new Setup {
      await(repository.count) shouldBe 0
      await(repository.insertSub(testValid))
      await(repository.count) shouldBe 1
      val result = await(repository.getSubscription("transId1","regime1","sub"))
      result shouldBe Some(Subscription("transId1","regime1","sub","url1"))
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
      val secondResponse = await(repository.insertSub(sub.copy(callbackUrl = "newUrl")))

      firstResponse shouldBe UpsertResult(0,1,Seq())
      secondResponse shouldBe UpsertResult(1,0,Seq())
    }
  }

    "deletesub" should {
      "only delete a single subscription" in new Setup{
        await(repository.count) shouldBe 0
        await(repository.insertSub(sub)) shouldBe UpsertResult(0,1,List())
        await(repository.insertSub(secondSub)) shouldBe UpsertResult(0,1,List())
        await(repository.count) shouldBe 2
        await(repository.deleteSub("transId1","regime1","sub")) shouldBe DeletedSub
        await(repository.count) shouldBe 1

        val result = await(repository.getSubscription("transId1","test1","PAYE"))
        result shouldBe Some(Subscription("transId1","test1","PAYE","url1"))
        result.head.subscriber shouldBe "PAYE"
      }

      "not delete a subscription when the subscription does not exist" in new Setup{
        await(repository.count) shouldBe 0
        await(repository.insertSub(sub))
        await(repository.insertSub(secondSub))
        await(repository.count) shouldBe 2
        await(repository.deleteSub("transId1","test","CTabc"))
        await(repository.count) shouldBe 2

      }
    }

  "wipeTestData" should {
    "remove all test data from submissions status" in new Setup {
      val result = await(repository.wipeTestData())
      result.hasErrors shouldBe false
    }
  }
}
