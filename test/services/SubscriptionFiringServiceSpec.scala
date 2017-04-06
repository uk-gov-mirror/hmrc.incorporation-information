package services

import Helpers.JSONhelpers
import connectors.FiringSubscriptionsConnector
import models.{IncorpUpdate, IncorpUpdateResponse, QueuedIncorpUpdate, Subscription}
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import reactivemongo.api.commands.DefaultWriteResult
import repositories.{QueueRepository, SubscriptionsRepository}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.generic.SeqFactory
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by jackie on 06/04/17.
  */
class SubscriptionFiringServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with JSONhelpers {

  val mockFiringSubsConnector = mock[FiringSubscriptionsConnector]
  val mockQueueRepository = mock[QueueRepository]
  val mockSubscriptionsRepository = mock[SubscriptionsRepository]

  implicit val hc = HeaderCarrier()

  override def beforeEach() {
    resetMocks()
  }

  def resetMocks() = {
    reset(mockFiringSubsConnector)
    reset(mockQueueRepository)
    reset(mockSubscriptionsRepository)
  }

  trait mockService extends SubscriptionFiringService {
    val firingSubsConnector = mockFiringSubsConnector
    val queueRepository = mockQueueRepository
    val subscriptionsRepository = mockSubscriptionsRepository

    implicit val hc = HeaderCarrier()

  }

  trait Setup {
    val service = new mockService {}
  }


  "fireIncorpUpdate" should {
    "return a Future of a Sequence of booleans when an incorp update has been successfully fired" in new Setup {
      val incorpUpdate = IncorpUpdate("transId1", "awaiting", None, None, "timepoint", None)
      val queuedIncorpUpdate = QueuedIncorpUpdate(DateTime.now, incorpUpdate)
      val sub = Subscription("transId1", "CT", "subscriber", "www.test.com")
      val incorpUpdateResponse = IncorpUpdateResponse("CT", "subscriber", "www.test.com", incorpUpdate)
      when(mockSubscriptionsRepository.getSubscriptions(incorpUpdate.transactionId)).thenReturn(Seq(sub))
      when(mockFiringSubsConnector.connectToAnyURL(incorpUpdateResponse, "www.test.com")(hc)).thenReturn(Future(HttpResponse(200)))
      when(mockSubscriptionsRepository.deleteSub(sub.transactionId, sub.regime, sub.subscriber)).thenReturn(Future(DefaultWriteResult(true, 1, Seq(), None, Some(1), Some(""))))
      when(mockQueueRepository.removeQueuedIncorpUpdate(sub.transactionId)).thenReturn(Future(true))

      val result = await(service.fireIncorpUpdate(queuedIncorpUpdate))
      result shouldBe Seq(true)
    }
  }

}
