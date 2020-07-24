import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "incorporation-information"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  private val akkaVersion     = "2.5.23"

  val compile = Seq(
    ws,
    "com.enragedginger" %% "akka-quartz-scheduler" % "1.8.1-akka-2.5.x",
    "uk.gov.hmrc" %% "bootstrap-play-26" % "1.8.0",
    "uk.gov.hmrc" %% "domain" % "5.9.0-play-26",
    "uk.gov.hmrc" %% "mongo-lock" % "6.23.0-play-26",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.30.0-play-26",
    "com.typesafe.play" %% "play-json-joda" % "2.6.10",
    "com.typesafe.akka" %% "akka-stream"    % akkaVersion     force(),
    "com.typesafe.akka" %% "akka-protobuf"  % akkaVersion     force(),
    "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion     force(),
    "com.typesafe.akka" %% "akka-actor"     % akkaVersion     force()
  )

  def test(scope: String = "test,it") = Seq(
    "org.scalatest" %% "scalatest" % "3.0.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.0" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.github.tomakehurst" % "wiremock-jre8" % "2.25.1" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.mockito" % "mockito-all" % "2.0.2-beta" % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "4.21.0-play-26" % scope
  )
}