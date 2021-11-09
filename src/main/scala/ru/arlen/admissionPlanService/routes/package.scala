package ru.arlen.admissionPlanService

import io.circe.{Decoder, Encoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Accept
import org.http4s._
import ru.arlen.abiturientService.httpClient
import ru.arlen.abiturientService.httpClient.HttpClient
import ru.arlen.admissionPlanService.configuration.Configuration
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.{RIO, Task}

package object routes {
  type APIEnv = Configuration with HttpClient with Clock with Console with Blocking

  final class AdmissionPlanAPI[R <: APIEnv] {
    // для совместимости с IO
    type AdmissionPlanAPITask[A] = RIO[R, A]

    val dsl = Http4sDsl[AdmissionPlanAPITask]

    implicit def jsonDecoder[A](implicit decoder: Decoder[A]): EntityDecoder[AdmissionPlanAPITask, A] = jsonOf[AdmissionPlanAPITask, A]
    implicit def jsonEncoder[A](implicit encoder: Encoder[A]): EntityEncoder[AdmissionPlanAPITask, A] = jsonEncoderOf[AdmissionPlanAPITask, A]

    import dsl._

    def create: RIO[Configuration, Unit] = Task()

    def routes: HttpRoutes[AdmissionPlanAPITask] = HttpRoutes.of[AdmissionPlanAPITask] {
      case req @ POST -> Root / "api" / "v1" / "admissionPlan" =>
        // декодируем полученный запрос в AbiturientRequestDTO
        req.decode[AbiturientRequestDTO] { abiturientRequestDTO =>

          Created(Task[AdmissionPlanResponse](AbitiruentRequestAccepted(1)))
        }

      case req @ POST -> Root / "api" / "v2" / "admissionPlan" =>
        // декодируем полученный запрос в AbiturientRequestDTO
        req.decode[AbiturientRequestDTO] { abiturientRequestDTO =>
          // реализовать запрос к сервису AdmissionPlan
          val response: AbiturientAPITask[AdmissionPlanResponse] = for {
            client <- httpClient.client
            uri = Uri.fromString("http://localhost:8080/api/v1/abiturient/request/acceptance").toOption.get
            request = Request[Task](
              method = POST,
              uri = uri,
              headers = Headers(List(Accept(MediaType.application.json)))
            ).withEntity[AdmissionPlanResponse](AbitiruentRequestAccepted(1))
            // делаем запрос request и ожидаем json, котрый десериализуется в AdmissionPlanResponse
            result <- client.expect[AdmissionPlanResponse](request)
          } yield result

          for {
            fiber <- response.delay(5 seconds).forkDaemon
            response <- Created(AdmissionPlanResponse.accepted(1))
          } yield response

          response.foldM(ex => InternalServerError(ex.getMessage), v => Ok(v.toString))
        }
    }
  }

}

