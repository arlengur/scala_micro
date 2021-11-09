package ru.arlen.admissionPlanService

import cats.effect.{ExitCode => CatsExitCode}
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import ru.arlen.abiturientService.httpClient.HttpClient
import ru.arlen.admissionPlanService.configuration.Configuration
import ru.arlen.admissionPlanService.routes.AdmissionPlanAPI
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.interop.catz._

object AdmissionPlanApp extends App {

  type AdmissionPlanAppEnv = Configuration with Clock with HttpClient with Console with Blocking
  type AppTask[A] = RIO[AdmissionPlanAppEnv, A]

  val admissionPlanAppEnv = Configuration.live ++ HttpClient.live

  val server: ZIO[AdmissionPlanAppEnv, Throwable, Unit] = for {
    cfg <- configuration.load
    admissionPlanRoutes = new AdmissionPlanAPI[AdmissionPlanAppEnv]().routes
    httpApp = Router("" -> admissionPlanRoutes).orNotFound
    server <- ZIO.runtime[AdmissionPlanAppEnv].flatMap { implicit rts =>
      val ec = rts.platform.executor.asEC
      BlazeServerBuilder[AppTask](ec)
        .bindHttp(cfg.admissionPlanApi.port, cfg.admissionPlanApi.host)
        .withHttpApp(httpApp)
        .serve
        .compile[AppTask, AppTask, CatsExitCode]
        .drain
    }
  } yield server

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    AdmissionPlanApp.server
      .provideSomeLayer[ZEnv](admissionPlanAppEnv)
      .tapError(err => zio.console.putStrLn(s"Execution failed with: $err"))
      .exitCode
  }
}

