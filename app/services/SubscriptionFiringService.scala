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

package services

import javax.inject.{Inject, Singleton}

import connectors.FiringSubscriptionsConnector
import models.{IncorpUpdateResponse, QueuedIncorpUpdate}
import play.api.Logger
import reactivemongo.api.commands.DefaultWriteResult
import repositories._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class SubscriptionFiringServiceImpl @Inject()(
                                               fsConnector: FiringSubscriptionsConnector,
                                               injQueueRepo: QueueMongo,
                                               injSubRepo: SubscriptionsMongo
                                             ) extends SubscriptionFiringService {
  override val firingSubsConnector = fsConnector
  override val queueRepository = injQueueRepo.repo
  override val subscriptionsRepository = injSubRepo.repo

  implicit val hc = HeaderCarrier()
}

trait SubscriptionFiringService {
  val firingSubsConnector: FiringSubscriptionsConnector
  val queueRepository: QueueRepository
  val subscriptionsRepository: SubscriptionsRepository

  implicit val hc: HeaderCarrier

    def fireIncorpUpdateBatch(): Future[Seq[Seq[Boolean]]] = {

      queueRepository.getIncorpUpdates flatMap { updates =>
        Future.sequence( updates map { update =>
            fireIncorpUpdate(update)
          }
        )
      }
    }


    def fireIncorpUpdate(iu: QueuedIncorpUpdate): Future[Seq[Boolean]] = {

      subscriptionsRepository.getSubscriptions(iu.incorpUpdate.transactionId) flatMap { subscriptions =>
        Future.sequence( subscriptions map { sub =>
          val iuResponse: IncorpUpdateResponse = IncorpUpdateResponse(sub.regime, sub.subscriber, sub.callbackUrl, iu.incorpUpdate)

          firingSubsConnector.connectToAnyURL(iuResponse, sub.callbackUrl)(hc) flatMap { response =>
            response.status match {
              case 200 =>
                subscriptionsRepository.deleteSub(sub.transactionId, sub.regime, sub.subscriber).map(res => res match {
                  case DefaultWriteResult(true, _, _, _, _, _) => true
                  case _ => Logger.info(s"[SubscriptionFiringService][fireIncorpUpdate] Subscription with transactionId: ${sub.transactionId} failed to delete")
                    false
                })
                queueRepository.removeQueuedIncorpUpdate(sub.transactionId).map(res => res match {
                  case true => true
                  case false => Logger.info(s"[SubscriptionFiringService][fireIncorpUpdate] QueuedIncorpUpdate with transactionId: ${sub.transactionId} failed to delete")
                    false
                })
              case _ =>
                Logger.info(s"[SubscriptionFiringService][fireIncorpUpdate] Subscription with transactionId: ${sub.transactionId} failed to return a 200 response")
                queueRepository.updateTimestamp(sub.transactionId)
                Future(false)
            }
          }
        } )
      }
    }


}
