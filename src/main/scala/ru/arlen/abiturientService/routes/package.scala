package ru.arlen.abiturientService

import `true`.admissionPlanService.ZioAdmissionPlanService.AdmissionPlanAPIClient
import io.circe.Encoder._
import io.circe.{Decoder, Encoder}
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Accept
import ru.arlen.abiturientService.configuration.Configuration
import ru.arlen.abiturientService.dto.AbiturientRequestDTO
import ru.arlen.abiturientService.httpClient.HttpClient
import ru.arlen.admissionPlanService.services.AdmissionPlanResponse
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.interop.catz._
import zio.{RIO, Task}

package object routes {
  type APIEnv = Configuration with HttpClient with Clock with AdmissionPlanAPIClient with Console

  final class AbiturientAPI[R <: APIEnv] {
    // для совместимости с IO
    type AbiturientAPITask[A] = RIO[R, A]

    val dsl = Http4sDsl[AbiturientAPITask]

    implicit def jsonDecoder[A](implicit decoder: Decoder[A]): EntityDecoder[AbiturientAPITask, A] = jsonOf[AbiturientAPITask, A]
    implicit def jsonEncoder[A](implicit encoder: Encoder[A]): EntityEncoder[AbiturientAPITask, A] = jsonEncoderOf[AbiturientAPITask, A]

    import dsl._

    def create: RIO[Configuration, Unit] = Task()

    def routes: HttpRoutes[AbiturientAPITask] = HttpRoutes.of[AbiturientAPITask] {
      case req @ POST -> Root / "api" / "v1" / "abiturient" =>
        // декодируем полученный запрос в AbiturientRequestDTO
        req.decode[AbiturientRequestDTO] { abiturientRequestDTO =>
          // реализовать запрос к сервису AdmissionPlan
          val response: AbiturientAPITask[AdmissionPlanResponse] = for {
            client <- httpClient.client
            uri = Uri.fromString("http://localhost:8081/api/v1/admissionPlan").toOption.get
            request = Request[Task](
              method = POST,
              uri = uri,
              headers = Headers(List(Accept(MediaType.application.json)))
            ).withEntity(abiturientRequestDTO)
            // делаем запрос request и ожидаем json, котрый десериализуется в AdmissionPlanResponse
            result <- client.expect[AdmissionPlanResponse](request)
          } yield result

          response.foldM(ex => InternalServerError(ex.getMessage), {
            case a @ AbitiruentRequestAccepted(_) => Ok(a)
            case a @ AbitiruentRequestRejected(_) => Ok(a)
          })
        }

      case req @ POST -> Root / "api" / "v2" / "abiturient" =>
        // декодируем полученный запрос в AbiturientRequestDTO
        req.decode[AbiturientRequestDTO] { abiturientRequestDTO =>
          // реализовать запрос к сервису AdmissionPlan
          val resp = for {
            r <- AdmissionPlanAPIClient
              .checkAbiturientRequest(AbiturientRequest(abiturientRequestDTO))
            _ <- putStrLn(r.toString)
          } yield r
          resp.foldM(ex => InternalServerError(ex.getCause.getMessage), v => Ok(v.toString))
        }

      case req @ POST -> Root / "api" / "v1" / "abiturient" / "request" / "acceptance" =>
        // декодируем полученный запрос в AbiturientRequestDTO
        req.decode[AdmissionPlanResponse] { admissionPlanResponse =>
          println(admissionPlanResponse)
          Ok(Task("Updated"))
        }
    }
  }

}
