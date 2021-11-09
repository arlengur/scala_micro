package ru.arlen.abiturientService

import `true`.admissionPlanService.ZioAdmissionPlanService.AdmissionPlanAPIClient
import cats.effect.{ExitCode => CatsExitCode}
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import ru.arlen.abiturientService.configuration.Configuration
import ru.arlen.abiturientService.httpClient.HttpClient
import ru.arlen.abiturientService.routes.AbiturientAPI
import ru.arlen.api.PhoneBookApi
import zio._
import zio.clock.Clock
import zio.interop.catz._

object AbiturientApp extends App {

  type AbiturientAppEnv = Configuration with Clock with HttpClient with AdmissionPlanAPIClient
  type AppTask[A] = RIO[AbiturientAppEnv, A]

  val abiturientAppEnv = Configuration.live ++ httpClient.HttpClient.live ++ admissionPlanClient.live

  val server: ZIO[AbiturientAppEnv, Throwable, Unit] = for {
    // получили конфиг
    cfg <- configuration.load
    abiturientRoutes = new AbiturientAPI[AbiturientAppEnv]().routes
    httpApp = Router("" -> abiturientRoutes).orNotFound
    server <- ZIO.runtime[AbiturientAppEnv].flatMap { implicit rts =>
      // rts зио рантайм, используем его чтобы получить execution context, чтобы запустить BlazeServer
      val ec = rts.platform.executor.asEC
      BlazeServerBuilder[AppTask](ec)
        .bindHttp(cfg.abiturientApi.port, cfg.abiturientApi.host)
        .withHttpApp(httpApp)
        .serve
        // компилируем стрим
        .compile[AppTask, AppTask, CatsExitCode]
        // отказываемся от результатов
        .drain
    }
  } yield server

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    AbiturientApp.server
      .provideSomeLayer[ZEnv](abiturientAppEnv)
      .tapError(err => zio.console.putStrLn(s"Execution failed with: $err"))
      .exitCode
  }
}
