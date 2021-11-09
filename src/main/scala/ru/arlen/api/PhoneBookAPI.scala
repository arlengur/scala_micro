package ru.arlen.api

import io.circe.generic.auto._
import io.circe.{Decoder, Encoder}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes}
import ru.arlen.db.DBTransactor
import ru.arlen.dto._
import ru.arlen.services.PhoneBookService
import zio.RIO
import zio.interop.catz._
import zio.random.Random

class PhoneBookApi[R <: PhoneBookService.PhoneBookService with DBTransactor with Random] {

  type PhoneBookTask[A] = RIO[R, A]

  val dsl = Http4sDsl[PhoneBookTask]
  import dsl._

  implicit def jsonDecoder[A](implicit decoder: Decoder[A]): EntityDecoder[PhoneBookTask, A] = jsonOf[PhoneBookTask, A]
  implicit def jsonEncoder[A](implicit encoder: Encoder[A]): EntityEncoder[PhoneBookTask, A] = jsonEncoderOf[PhoneBookTask, A]

  def route = HttpRoutes.of[PhoneBookTask] {
    case GET -> Root / list =>
      PhoneBookService
        .list()
        .foldM(
          err => NotFound(),
          result => Ok(result)
        )
    case GET -> Root / phone =>
      PhoneBookService
        .find(phone)
        .foldM(
          err => NotFound(),
          result => Ok(result)
        )
    case req @ POST -> Root =>
      (for {
        record <- req.as[PhoneRecordDTO] // as сериализует body в case class
        result <- PhoneBookService.insert(record)
      } yield result).foldM(
        err => BadRequest(err.getMessage()),
        result => Ok(result)
      )
    case req @ PUT -> Root / id =>
      (for {
        record <- req.as[PhoneRecordDTO]
        _      <- PhoneBookService.update(id, record)
      } yield ()).foldM(
        err => BadRequest(err.getMessage()),
        result => Ok(result)
      )
    case DELETE -> Root / id =>
      PhoneBookService
        .delete(id)
        .foldM(
          err => BadRequest("Not found"),
          result => Ok(result)
        )
  }
}
