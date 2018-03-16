/*
 * Copyright 2018 HM Revenue & Customs
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

package services

import Helpers.JSONhelpers
import connectors.IncorporationAPIConnector
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import org.joda.time.DateTime
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.commands.{UpdateWriteResult, Upserted, WriteError}
import reactivemongo.bson.{BSONString, BSONValue}
import repositories._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class IncorpUpdateServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with JSONhelpers{

  val mockIncorporationCheckAPIConnector = mock[IncorporationAPIConnector]
  val mockIncorpUpdateRepository = mock[IncorpUpdateRepository]
  val mockTimepointRepository = mock[TimepointRepository]
  val mockQueueRepository = mock[QueueRepository]
  val mockSubscriptionService = mock[SubscriptionService]
  val mockSubRepo = mock[SubscriptionsMongoRepository]

  implicit val hc = HeaderCarrier()

  override def beforeEach() {
    resetMocks()
  }

  def resetMocks() = {
    reset(mockIncorporationCheckAPIConnector)
    reset(mockIncorpUpdateRepository)
    reset(mockSubscriptionService)
    reset(mockTimepointRepository)
    reset(mockQueueRepository)
    reset(mockSubRepo)
  }

  trait Setup {
    val service = new IncorpUpdateService {
      val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
      val incorpUpdateRepository = mockIncorpUpdateRepository
      val timepointRepository = mockTimepointRepository
      val queueRepository = mockQueueRepository
      val subscriptionService = mockSubscriptionService
      val noRAILoggingDay = "Mon"
      val noRAILoggingTime = "08:00:00_17:00:00"
    }
  }

  val timepoint = TimePoint("id", "old timepoint")
  val timepointOld = "old-timepoint"
  val timepointNew = "new-timepoint"
  val timepointSeq = Seq(timepointOld,timepointNew)
  val incorpUpdate = IncorpUpdate("transId", "accepted", None, None, timepointOld, None)
  val incorpUpdate2 = IncorpUpdate("transId2", "accepted", None, None, timepointOld, None)
  val incorpUpdate3 = IncorpUpdate("transId3", "rejected", None, None, timepointOld, None)
  val incorpUpdateNew = IncorpUpdate("transIdNew", "accepted", None, None, timepointNew, None)
  val incorpUpdates = Seq(incorpUpdate, incorpUpdateNew)
  val seqOfIncorpUpdates = Seq(incorpUpdate, incorpUpdate2, incorpUpdate3)
  val emptyUpdates = Seq()
  val queuedIncorpUpdate = QueuedIncorpUpdate(DateTime.now, incorpUpdate)
  val queuedIncorpUpdate2 = QueuedIncorpUpdate(DateTime.now, incorpUpdateNew)
  val transId = "transId123"
  val regime = "CT"
  val subscriber = "SCRS"
  val url = "www.test.com"
  val sub = Subscription(transId, regime, subscriber, url)

  "fetchIncorpUpdates" should {
    "return some updates" in new Setup {
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(Some(sub)))
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(incorpUpdates))

      val response = service.fetchIncorpUpdates
      response.size shouldBe 2
    }

    "return no updates when they are no updates available" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))

      val response = service.fetchIncorpUpdates
      response.size shouldBe 0
    }
  }

  "fetchSpecificIncorpUpdates" should {
    "return a single update" in new Setup {
     when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(Some(timepointOld))).thenReturn(Future.successful(Seq(incorpUpdate)))

      val response = await(service.fetchSpecificIncorpUpdates(Some(timepointOld)))
      response shouldBe incorpUpdate
    }
  }


  "storeIncorpUpdates" should {
    "return InsertResult(2, 0, Seq(), 0) when one update with 2 incorps has been inserted with CT subscriptions" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates)).thenReturn(InsertResult(2, 0, Seq(),0, incorpUpdates))
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(Some(sub)))

      val response = await(service.storeIncorpUpdates(Future.successful(incorpUpdates)))
      response shouldBe InsertResult(2, 0, Seq(), 0, incorpUpdates)
    }

    "return InsertResult(2, 0, Seq(), 2) when one update with 2 incorps has been inserted without CT subscriptions" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates)).thenReturn(InsertResult(2, 0, Seq(), 0, incorpUpdates))
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(None))

      val response = await(service.storeIncorpUpdates(Future.successful(incorpUpdates)))
      response shouldBe InsertResult(2, 0, Seq(), 2, incorpUpdates)
    }

    "return InsertResult(2, 0, Seq(), 1) when one update with 2 incorps has been inserted one with one without CT subscriptions" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates)).thenReturn(InsertResult(2, 0, Seq(), 0, incorpUpdates))
      when(mockSubscriptionService.getSubscription(Matchers.eq("transId"), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(Some(sub)))
      when(mockSubscriptionService.getSubscription(Matchers.eq("transIdNew"), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(None))

      val response = await(service.storeIncorpUpdates(Future.successful(incorpUpdates)))
      response shouldBe InsertResult(2, 0, Seq(), 1, incorpUpdates)
    }

    "return InsertResult(0, 0, Seq()) when there are no updates to store" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(InsertResult(0, 0, Seq()))

      val response = await(service.storeIncorpUpdates(Future.successful(emptyUpdates)))
      response shouldBe InsertResult(0, 0, Seq(), 0)
    }

    "return an InsertResult containing errors, when a failure occurred whilst adding incorp updates to the main collection" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      val writeError = WriteError(0, 121, "Invalid Incorp Update could not be stored")
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(InsertResult(0, 0, Seq(writeError)))

      val response = await(service.storeIncorpUpdates(emptyUpdates))
      response shouldBe InsertResult(0, 0, Seq(writeError))
    }
  }

  "storeSpecificIncorpUpdate" should {
    "return an UpdateWriteResult when a single IncorpUpdate is input" in new Setup {
      val upserted = Seq(Upserted(1,BSONString("")))
      val UWR = UpdateWriteResult(true,1,1,upserted,Seq(WriteError(1,1,"")),None,None,None)
      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(incorpUpdate)).thenReturn(UWR)

      val response = await(service.storeSpecificIncorpUpdate(Future.successful(incorpUpdate)))
      response shouldBe UpdateWriteResult(true,1,1,upserted,Seq(WriteError(1,1,"")),None,None,None)
    }
  }

    "latestTimepoint" should {
    "return the latest timepoint when two have been given" in new Setup {
      val response = service.latestTimepoint(incorpUpdates)
      response shouldBe timepointNew
    }
  }

  "updateNextIncorpUpdateJobLot" should {

    "return a string stating that states 'No Incorporation updates were fetched'" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(Future(InsertResult(0, 0, Seq())))

      val response = await(service.updateNextIncorpUpdateJobLot)
      response shouldBe InsertResult(0,0,Seq())

    }

    "return a string stating that the timepoint has been updated to 'new timepoint'" in new Setup {
      val newTimepoint = timepointNew
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(Some(sub)))
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepointOld)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Matchers.eq(Some(timepointOld)))(Matchers.any())).thenReturn(Future.successful(incorpUpdates))

      when(mockIncorpUpdateRepository.storeIncorpUpdates(Matchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq(), 0, incorpUpdates)))

      when(mockQueueRepository.storeIncorpUpdates(Matchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq())))

      val captor = ArgumentCaptor.forClass(classOf[String])

      when(mockTimepointRepository.updateTimepoint(Matchers.any())).thenReturn(Future.successful(newTimepoint))

      val response = await(service.updateNextIncorpUpdateJobLot)
      verify(mockTimepointRepository).updateTimepoint(captor.capture())
      captor.getValue shouldBe newTimepoint
      response shouldBe InsertResult(2,0,Seq(),0,incorpUpdates)
    }
  }

  "updateSpecificIncorpUpdateByTP" should {

    "return a Sequence of trues when a sequence of TPs is input and there is a queue entry for each" in new Setup {

      val upserted = Seq(Upserted(1, BSONString("")))
      val UWR = UpdateWriteResult(ok = true, 1, 1, upserted, Seq(WriteError(1, 1, "")), None, None, None)

      val seqOfQueuedIncorpUpdates = Seq(queuedIncorpUpdate,queuedIncorpUpdate2)

      when(mockQueueRepository.removeQueuedIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockQueueRepository.getIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(Some(queuedIncorpUpdate)))

      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(Matchers.any())).thenReturn(Future.successful(UWR))

      when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Seq(incorpUpdate)), Future.successful(Seq(incorpUpdateNew)))

      when(mockQueueRepository.storeIncorpUpdates(Matchers.any()))
        .thenReturn(Future.successful(InsertResult(1,0,Nil)))

      val response = await(service.updateSpecificIncorpUpdateByTP(timepointSeq))
      response shouldBe Seq(true,true)

    }
    "return a Sequence of false when a sequence of TPs is input and queue entries don't exist" in new Setup {

      val upserted = Seq(Upserted(1, BSONString("")))
      val UWR = UpdateWriteResult(ok = true, 1, 1, upserted, Seq(WriteError(1, 1, "")), None, None, None)

      val seqOfQueuedIncorpUpdates = Seq(queuedIncorpUpdate,queuedIncorpUpdate2)

      when(mockQueueRepository.removeQueuedIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockQueueRepository.getIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(None))

      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(Matchers.any())).thenReturn(Future.successful(UWR))

      when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Seq(incorpUpdate)), Future.successful(Seq(incorpUpdateNew)))

      when(mockQueueRepository.storeIncorpUpdates(Matchers.any()))
        .thenReturn(Future.successful(InsertResult(1,0,Nil)))

      val response = await(service.updateSpecificIncorpUpdateByTP(timepointSeq))
      response shouldBe Seq(false,false)

    }

    "return a Sequence of trues when a sequence of TPs is input and queue entries don't exist but the for no queue switch is set" in new Setup {

      val upserted = Seq(Upserted(1, BSONString("")))
      val UWR = UpdateWriteResult(ok = true, 1, 1, upserted, Seq(WriteError(1, 1, "")), None, None, None)

      val seqOfQueuedIncorpUpdates = Seq(queuedIncorpUpdate,queuedIncorpUpdate2)

      when(mockQueueRepository.removeQueuedIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockQueueRepository.getIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(None))

      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(Matchers.any())).thenReturn(Future.successful(UWR))

      when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Seq(incorpUpdate)), Future.successful(Seq(incorpUpdateNew)))

      when(mockQueueRepository.storeIncorpUpdates(Matchers.any()))
        .thenReturn(Future.successful(InsertResult(1,0,Nil)))

      val response = await(service.updateSpecificIncorpUpdateByTP(timepointSeq,true))
      response shouldBe Seq(true,true)

    }

  }

  "createQueuedIncorpUpdate" should {
    "return a correctly formatted QueuedIncorpUpdate when given an IncorpUpdate" in new Setup {

      val fResult = service.createQueuedIncorpUpdates(Seq(incorpUpdate))
      val result = await(fResult)

      result.head.copy(timestamp = queuedIncorpUpdate.timestamp) shouldBe queuedIncorpUpdate
      result.head.timestamp.getMillis shouldBe (queuedIncorpUpdate.timestamp.getMillis +- 1000)
    }
  }

  "copyToQueue" should {
    "return true if a Seq of QueuedIncorpUpdates have been copied to the queue" in new Setup {
      when(mockQueueRepository.storeIncorpUpdates(Seq(queuedIncorpUpdate))).thenReturn(Future(InsertResult(1, 0, Seq())))

      val result = await(service.copyToQueue(Seq(queuedIncorpUpdate)))
      result shouldBe true
    }

    "return false if a Seq of QueuedIncorpUpdates have not been copied to the queue" in new Setup {
      when(mockQueueRepository.storeIncorpUpdates(Seq(queuedIncorpUpdate))).thenReturn(Future(InsertResult(0, 1, Seq())))

      val result = await(service.copyToQueue(Seq(queuedIncorpUpdate)))
      result shouldBe false
    }
  }

  "checkForInterest" should {

    "return the no alerts were raised where there's an interest registered" in new Setup {
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(Some(sub)))

      val result = await(service.alertOnNoCTInterest(seqOfIncorpUpdates))

      result shouldBe 0
    }

    "return 2 where there were two incorps without an interest registered" in new Setup {
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(None))

      val result = await(service.alertOnNoCTInterest(incorpUpdates))

      result shouldBe 2
    }
    "return 1 alerts when 2 out of 3 incorps have an interested registered" in new Setup {
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(Some(sub)), Future(None), Future(Some(sub)))

      val result = await(service.alertOnNoCTInterest(seqOfIncorpUpdates))

      result shouldBe 1
    }

    "return 0 alerts if no incorps are processed" in new Setup {
      val result = await(service.alertOnNoCTInterest(emptyUpdates))

      result shouldBe 0
    }
  }

}


