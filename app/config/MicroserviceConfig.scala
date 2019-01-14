/*
 * Copyright 2019 HM Revenue & Customs
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

package config

import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.play.config.inject.ServicesConfig
import utils.Base64

import scala.util.Try

class MicroserviceConfigImpl @Inject()(val config: ServicesConfig) extends MicroserviceConfig

trait MicroserviceConfig {
  protected val config: ServicesConfig

  private def getConfigInt(configKey: String) = config.getConfInt(configKey, throw new Exception(s"$configKey key not found"))
  private def getConfigString(configKey: String) = config.getConfString(configKey, throw new Exception(s"$configKey key not found"))

  lazy val incorpFrontendStubUrl = config.getConfString("incorp-update-api.stub-url", throw new Exception("incorp-update-api.stub-url not found"))

  lazy val companiesHouseUrl = config.getConfString("incorp-update-api.url", throw new Exception("incorp-update-api.url not found"))

  lazy val incorpUpdateCohoApiAuthToken = config.getConfString("incorp-update-api.token", throw new Exception("incorp-update-api.token not found"))

  lazy val incorpUpdateItemsToFetch = config.getConfString("incorp-update-api.itemsToFetch", throw new Exception("incorp-update-api.itemsToFetch not found"))

  lazy val queueFetchSize = config.getConfInt("fire-subs-job.queueFetchSizes", {
    Logger.warn("[Config] fire-subs-job.queueFetchSizes missing, defaulting to 1")
    1
  })

  lazy val queueFailureDelay = config.getConfInt("fire-subs-job.queueFailureDelaySeconds", throw new Exception("fire-subs-api.queueFailureDelaySeconds not found"))

  lazy val queueRetryDelay = config.getConfInt("fire-subs-job.queueRetryDelaySeconds", throw new Exception("fire-subs-api.queueFailureDelaySeconds not found"))

  lazy val cohoPublicBaseUrl = config.getConfString("public-coho-api.baseUrl", throw new Exception("public-coho-api.baseUrl not found"))

  lazy val cohoPublicApiAuthToken = config.getConfString("public-coho-api.authToken", throw new Exception("public-coho-api.authToken not found"))

  lazy val nonSCRSPublicApiAuthToken = config.getConfString("public-coho-api.authTokenNonSCRS", throw new Exception("non-scrs-public-coho-api.authToken not found"))

  lazy val cohoStubbedUrl = config.getConfString("public-coho-api.stub-url", throw new Exception("public-coho-api.stub-url not found"))

  lazy val forcedSubscriptionDelay = getConfigInt("forced-submission-delay-minutes")

  lazy val noRegisterAnInterestLoggingDay = config.getConfString("rai-alert-logging-day", throw new Exception("rai-alert-logging-day not found"))

  lazy val noRegisterAnInterestLoggingTime = config.getConfString("rai-alert-logging-time", throw new Exception("rai-alert-logging-time not found"))

  lazy val knownSCRSServices = Base64.decode(config.getConfString("scrs-services", throw new Exception("scrs-services not found")))

  lazy val transactionIdToPoll: String = getConfigString("transaction-id-to-poll")
  lazy val crnToPoll: String = getConfigString("crn-to-poll")

  lazy val useHttpsFireSubs: Boolean = Try(config.getBoolean("use-https-fire-subs")).recover{case _ => throw new Exception("use-https-fire-subs not found")}.get

}

