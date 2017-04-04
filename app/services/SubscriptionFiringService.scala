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

import connectors.IncorporationCheckAPIConnector
import repositories._

import scala.concurrent.Future

@Singleton
class SubscriptionFiringServiceImpl @Inject()(
                                               injConnector: IncorporationCheckAPIConnector,
                                               injIncorpRepo: IncorpUpdateMongo,
                                               injTimepointRepo: TimepointMongo,
                                               injQueueRepo: QueueMongo
                                             ) extends SubscriptionFiringService {
  override val incorporationCheckAPIConnector = injConnector
  override val incorpUpdateRepository = injIncorpRepo.repo
  override val timepointRepository = injTimepointRepo.repo
  override val queueRepository = injQueueRepo.repo
}

trait SubscriptionFiringService {

  val incorporationCheckAPIConnector: IncorporationCheckAPIConnector
  val incorpUpdateRepository: IncorpUpdateRepository
  val timepointRepository: TimepointRepository
  val queueRepository: QueueRepository


//  def fireIncorpUpdateBatch(): Future[Seq[Boolean]] = {
//
//  }

}
