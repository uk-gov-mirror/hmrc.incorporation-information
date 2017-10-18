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

package config

import java.util.Base64
import javax.inject.{Inject, Named, Singleton}

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.{Application, Configuration, Logger, Play}
import repositories.TimepointMongo
import services.IncorpUpdateService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, MicroserviceFilterSupport}
import uk.gov.hmrc.play.scheduling.{RunningOfScheduledJobs, ScheduledJob}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
  lazy val controllerConfigs = ControllerConfiguration.controllerConfigs
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
  override val auditConnector = MicroserviceAuditConnector

  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceAuthFilter extends AuthorisationFilter with MicroserviceFilterSupport {
  override lazy val authParamsConfig = AuthParamsControllerConfiguration
  override lazy val authConnector = MicroserviceAuthConnector

  override def controllerNeedsAuth(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuth
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode with MicroserviceFilterSupport with RunningOfScheduledJobs {
  override lazy val scheduledJobs = Play.current.injector.instanceOf[Jobs].lookupJobs()
  override val auditConnector = MicroserviceAuditConnector
  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = Some(MicroserviceAuthFilter)

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override def onStart(app : play.api.Application) : scala.Unit = {

    val confVersion = app.configuration.getString("config.version")
    Logger.info(s"[Config] Config Version = ${confVersion}")

    val metricsKey = "microservice.metrics.graphite.enabled"
    val metricsEnabled = app.configuration.getString(metricsKey)
    Logger.info(s"[Config] ${metricsKey} = ${metricsEnabled}")

    reFetchIncorpInfo(app)

    resetTimepoint(app)

    super.onStart(app)
  }

  private def reFetchIncorpInfo(app: Application): Future[Unit] = {

    app.configuration.getString("timepointList") match {
      case None => Future.successful(Logger.info(s"[Config] No timepoints to re-fetch"))
      case Some(timepointList) =>
        val tpList = new String(Base64.getDecoder.decode(timepointList), "UTF-8")
        Logger.info(s"[Config] List of timepoints are $tpList")
        app.injector.instanceOf[IncorpUpdateService].updateSpecificIncorpUpdateByTP(tpList.split(","))(HeaderCarrier()) map { result =>
          Logger.info(s"Updating incorp data is switched on - result = $result")
        }
    }
  }

  private def resetTimepoint(app: Application): Future[Boolean] = {
    implicit val ex: ExecutionContext = app.materializer.executionContext.prepare()

    app.configuration.getString("microservice.services.reset-timepoint-to").fold{
      Logger.info("[ResetTimepoint] Could not find a timepoint to reset to (config key microservice.services.reset-timepoint-to)")
      Future(false)
    }{
      timepoint =>
        Logger.info(s"[ResetTimepoint] Found timepoint from config - $timepoint")
        app.injector.instanceOf[TimepointMongo].repo.resetTimepointTo(timepoint)
    }
  }
}

trait JobsList {
  def lookupJobs(): Seq[ScheduledJob] = Seq()
}

@Singleton
class Jobs @Inject()(@Named("incorp-update-job") injIncUpdJob: ScheduledJob,
                     @Named("fire-subs-job") injFireSubsJob: ScheduledJob,
                     @Named("metrics-job") injMetricsJob: ScheduledJob,
                     @Named("proactive-monitoring-job") injProMonitoringJob: ScheduledJob
                    ) extends JobsList {
  override def lookupJobs(): Seq[ScheduledJob] =
    Seq(
      injIncUpdJob,
      injFireSubsJob,
      injMetricsJob,
      injProMonitoringJob
    )
}
