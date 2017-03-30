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

package controllers


import models.IncorpUpdate
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Action
import repositories.{DeletedSub, FailedSub, IncorpExists, SuccessfulSub}
import services.SubscriptionService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

// TODO - LJ - File should be renamed (shouldn't have Impl in the name)

// TODO - LJ - Should be singleton (potentially in the Module?)
class SubscriptionControllerImpl extends SubscriptionController {
  override protected val service = SubscriptionService
}

trait SubscriptionController extends BaseController {

  protected val service: SubscriptionService

  def checkSubscription(transactionId: String, regime: String, subscriber: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[JsObject] { js =>
        val callbackUrl = (js \ "SCRSIncorpSubscription" \ "callbackUrl").as[String]
        service.checkForSubscription(transactionId, regime, subscriber, callbackUrl).map {
          // TODO - LJ - not exhaustive - DeleteSub :-(
          case IncorpExists(update) => {
            Ok(Json.toJson(update)(IncorpUpdate.writes(callbackUrl, transactionId)))
          }
          // TODO - LJ - Why does this responses have (non-JSON) text?
          case SuccessfulSub => {
            Accepted("You have successfully added a subscription")
          }
          case FailedSub => {
            InternalServerError
          }
        }
      }

  }


  def removeSubscription(transactionId: String, regime: String, subscriber: String) = Action.async {
    implicit request =>
        service.deleteSubscription(transactionId, regime, subscriber).map {
           {
             // TODO - LJ - Why do these responses have (non-JSON) text?
            case DeletedSub => Ok("subscription has been deleted")
            case FailedSub => NotFound("The subscription does not exist")
            case _ => InternalServerError
          }
        }
  }
}
