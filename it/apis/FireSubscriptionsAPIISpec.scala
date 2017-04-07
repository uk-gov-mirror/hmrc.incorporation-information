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

package apis

import helpers.IntegrationSpecBase
import models.{IncorpUpdate, IncorpUpdateResponse, QueuedIncorpUpdate, Subscription}
import org.joda.time.DateTime
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.{IncorpUpdateMongo, QueueMongo, SubscriptionsMongo}
import services.SubscriptionFiringService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global


class FireSubscriptionsAPIISpec extends IntegrationSpecBase {


  val mockUrl = s"http://$wiremockHost:$wiremockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$wiremockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  lazy val reactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]


  class Setup {
    val mongo = new SubscriptionsMongo
    val incRepo = new IncorpUpdateMongo(reactiveMongoComponent).repo
    val subRepo = mongo.repo
    val queueRepo = new QueueMongo(reactiveMongoComponent).repo

    def insert(sub: Subscription) = await(subRepo.insert(sub))
    def insert(update: IncorpUpdate) = await(incRepo.insert(update))
    def insert(queuedIncorpUpdate: QueuedIncorpUpdate) = await(queueRepo.insert(queuedIncorpUpdate))
    def subCount = await(subRepo.count)
    def incCount = await(incRepo.count)
    def queueCount = await(queueRepo.count)

    implicit val hc = HeaderCarrier()
  }

  override def beforeEach() = new Setup {
    await(subRepo.drop)
    await(subRepo.ensureIndexes)
    await(incRepo.drop)
    await(incRepo.ensureIndexes)
    await(queueRepo.drop)
    await(queueRepo.ensureIndexes)
  }

  override def afterEach() = new Setup {
    await(subRepo.drop)
    await(incRepo.drop)
    await(queueRepo.drop)
  }

  val incorpUpdate = IncorpUpdate("transId1", "awaiting", None, None, "timepoint", None)
  val incorpUpdate2 = IncorpUpdate("transId2", "awaiting", None, None, "timepoint", None)
  val queuedIncorpUpdate = QueuedIncorpUpdate(DateTime.now, incorpUpdate)
  val queuedIncorpUpdate2 = QueuedIncorpUpdate(DateTime.now, incorpUpdate2)
  val sub = Subscription("transId1", "CT", "subscriber", s"$mockUrl/mockUri")
  val sub2 = Subscription("transId1", "PAYE", "subscriber", s"$mockUrl/mockUri")
  val sub3 = Subscription("transId2", "CT", "subscriber", s"$mockUrl/mockUri")
  val incorpUpdateResponse = IncorpUpdateResponse("CT", "subscriber", mockUrl, incorpUpdate)


  "fireIncorpUpdateBatch" should {
    "return a Sequence of true values when one incorp update has been successfully fired" in new Setup {
      subCount shouldBe 0
      insert(sub)
      subCount shouldBe 1

      queueCount shouldBe 0
      insert(queuedIncorpUpdate)
      queueCount shouldBe 1

      val service = app.injector.instanceOf[SubscriptionFiringService]
      stubPost("/mockUri", 200, "")

      val fResult = service.fireIncorpUpdateBatch
      val result = await(fResult)
      result shouldBe Seq(Seq(true))
    }

    "return a sequence of (false, true) when two subscriptions with the same transId have successfully returned 200 HTTP responses and then deleted" +
      "the first false value is due to that another subscription exists with the same transId at the time and therefore the queued incorp update" +
      "cannot be deleted when the first subscription has been successfully tried and deleted" in new Setup {
      subCount shouldBe 0
      insert(sub)
      subCount shouldBe 1
      insert(sub2)
      subCount shouldBe 2

      queueCount shouldBe 0
      insert(queuedIncorpUpdate)
      queueCount shouldBe 1

      val service = app.injector.instanceOf[SubscriptionFiringService]
      stubPost("/mockUri", 200, "")

      val fResult = service.fireIncorpUpdateBatch
      val result = await(fResult)
      result shouldBe Seq(Seq(false, true))//the test run keeps swapping these values around, I think false should always be first
    }


    "return there true values when three updates have been successfully fired" in new Setup {
      subCount shouldBe 0
      insert(sub)
      subCount shouldBe 1
      insert(sub2)
      subCount shouldBe 2
      insert(sub3)
      subCount shouldBe 3

      queueCount shouldBe 0
      insert(queuedIncorpUpdate)
      queueCount shouldBe 1
      insert(queuedIncorpUpdate2)
      queueCount shouldBe 2

      val service = app.injector.instanceOf[SubscriptionFiringService]
      stubPost("/mockUri", 200, "")

      val fResult = service.fireIncorpUpdateBatch
      val result = await(fResult)
      result shouldBe Seq(Seq(true, true), Seq(true))
    }


    "return a true value when an update has been fired that matches the transId of one of the two Subscriptions in the database" in new Setup {
      subCount shouldBe 0
      insert(sub3)
      subCount shouldBe 1
      insert(sub)
      subCount shouldBe 2

      queueCount shouldBe 0
      insert(queuedIncorpUpdate)
      queueCount shouldBe 1

      val service = app.injector.instanceOf[SubscriptionFiringService]
      stubPost("/mockUri", 200, "")

      val fResult = service.fireIncorpUpdateBatch
      val result = await(fResult)
      result shouldBe Seq(Seq(true))
      subCount shouldBe 1
    }

  }



}
