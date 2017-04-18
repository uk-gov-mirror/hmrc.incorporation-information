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

package controllers.test

import javax.inject.{Singleton, Named, Inject}

import play.api.mvc.Action
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.play.scheduling.{ScheduledJob, ExclusiveScheduledJob}

import scala.concurrent.ExecutionContext.Implicits.global
import constants.JobNames.INCORP_UPDATE

@Singleton
class ManualTriggerControllerImpl @Inject()(@Named("incorp-update-job") val incUpdatesJob: ScheduledJob) extends ManualTriggerController

trait ManualTriggerController extends BaseController {

  protected val incUpdatesJob: ScheduledJob

  def triggerJob(jobName: String) = Action.async {
    implicit request =>
      jobName match {
        case INCORP_UPDATE => triggerIncorpUpdateJob
      }
  }

  private def triggerIncorpUpdateJob = incUpdatesJob.execute map (res => Ok(res.message))
}
