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
import models.{IncorpUpdate, IncorpUpdateResponse}
import repositories._

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
}

trait SubscriptionFiringService {
  val firingSubsConnector: FiringSubscriptionsConnector
  val queueRepository: QueueRepository
  val subscriptionsRepository: SubscriptionsRepository

//  def fireIncorpUpdateBatch(): Future[Seq[Boolean]] = {
//    queueRepository.getIncorpUpdates.map { updates => updates.map {
//      update => for {
//        subs <- subscriptionsRepository.getSubscriptions(update.incorpUpdate.transactionId)
//      } yield subs.map {
//        sub => for {
//          iuResponse <- IncorpUpdateResponse.writes
//          response <- firingSubsConnector.connectToAnyURL(iuResponse, sub.callbackUrl)
//        } yield response {
//          response match {
//            case 200 => true
//            case _ => false
//        }
//        }
//      }
//    }
//    return Future(Seq(true))
//  }



    def fireIncorpUpdate(iu: IncorpUpdate): Future[Seq[Boolean]] = {

      subscriptionsRepository.getSubscriptions(iu.transactionId) flatMap { subscriptions =>
        Future.sequence( subscriptions map { sub =>
          val iuResponse: IncorpUpdateResponse = IncorpUpdateResponse(sub.regime, sub.subscriber, sub.callbackUrl, iu)

          firingSubsConnector.connectToAnyURL(iuResponse, sub.callbackUrl) flatMap { response =>
            response.status match {
              case 200 => //go ahead and delete the subscription and queuedIncorpUpdate
                Future(true)
              case _ => //need to log which one didnt return a 200, update the timepoint so firing the update is tried again later
                Future(false)
            }
          }
        } )
      }
    }





}
