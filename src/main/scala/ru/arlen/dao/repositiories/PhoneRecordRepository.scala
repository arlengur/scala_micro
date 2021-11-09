package ru.arlen.dao.repositiories

import zio.Has
import doobie.quill.DoobieContext
import io.getquill.CompositeNamingStrategy2
import io.getquill.Escape
import io.getquill.Literal
import ru.arlen.dao.entities.PhoneRecord
import ru.arlen.db.DBTransactor
import zio.ZLayer
import zio.ULayer

object PhoneRecordRepository {
  val dc: DoobieContext.Postgres[CompositeNamingStrategy2[Escape.type, Literal.type]] = DBTransactor.doobieContext
  import dc._

  type PhoneRecordRepository = Has[Service]

  trait Service {
    def find(phone: String): Result[Option[PhoneRecord]]
    def list(): Result[List[PhoneRecord]]
    def insert(phoneRecord: PhoneRecord): Result[Unit]
    def update(phoneRecord: PhoneRecord): Result[Unit]
    def delete(id: String): Result[Unit]
  }

  class Impl extends Service {

    val phoneRecordSchema = quote {
      // представляет таблицу в БД
      querySchema[PhoneRecord](""""PhoneRecord"""")
    }

    def list(): Result[List[PhoneRecord]] = {
      // пока это просто описание, но вообще run превращает описаание запроса в описание результата
      dc.run(phoneRecordSchema)
    } // SELECT "x"."id", "x"."phone", "x"."fio" FROM "PhoneRecord" "x"

    def find(phone: String): Result[Option[PhoneRecord]] =
      // lift поднимает переменную в контекст quill
      dc.run(phoneRecordSchema.filter(_.phone == lift(phone)))
        .map(_.headOption)
    // SELECT "x1"."id", "x1"."phone", "x1"."fio" FROM "PhoneRecord" "x1" WHERE "x1"."phone" = ?

    def insert(phoneRecord: PhoneRecord): Result[Unit] = dc.run(phoneRecordSchema.insert(lift(phoneRecord))).map(_ => ())

    def update(phoneRecord: PhoneRecord): Result[Unit] = dc
      .run(
        phoneRecordSchema
          .filter(_.id == lift(phoneRecord.id))
          .update(lift(phoneRecord))
      )
      .map(_ => ())
    // UPDATE "PhoneRecord" SET "id" = ?, "phone" = ?, "fio" = ? WHERE "id" = ?

    def delete(id: String): Result[Unit] = dc.run(phoneRecordSchema.filter(_.id == lift(id)).delete).map(_ => (()))

    def foo() = {

      val a = dc.run(
        phoneRecordSchema
          .filter(_.phone == lift("1234"))
          .filter(_.fio == lift("fio"))
      ) // SELECT "x8"."id", "x8"."phone", "x8"."fio" FROM "PhoneRecord" "x8" WHERE "x8"."phone" = ? AND "x8"."fio" = ?

      val b = quote {
        for {
          r1 <- phoneRecordSchema.filter(_.id == lift("12"))
          r2 <- phoneRecordSchema.filter(_.phone == lift("1234"))
        } yield (r2)
      }

      dc.run(b)
      // SELECT "x11"."id", "x11"."phone", "x11"."fio" FROM "PhoneRecord" "x10", "PhoneRecord" "x11" WHERE "x10"."id" = ? AND "x11"."phone" = ?

      val c = quote {
        for {
          q1 <- phoneRecordSchema.filter(r => r.id == lift("dddd"))
          q2 <- phoneRecordSchema.join(_.id == q1.id)
        } yield (q2.fio)
      }

      dc.run(c)
      // SELECT "x12"."fio" FROM "PhoneRecord" "r" INNER JOIN "PhoneRecord" "x12" ON "x12"."id" = "r"."id" WHERE "r"."id" = ?

      val d = quote {
        for {
          q1 <- phoneRecordSchema.filter(r => r.id == lift("dddd") || r.fio == lift("name"))
          q2 <- phoneRecordSchema.join(_.id == q1.id)
        } yield (q2.fio)
      }

      dc.run(d)
      // SELECT "x13"."fio" FROM "PhoneRecord" "r" INNER JOIN "PhoneRecord" "x13" ON "x13"."id" = "r"."id" WHERE "r"."id" = ? OR "r"."fio" = ?

    }

  }

  val live: ULayer[PhoneRecordRepository] = ZLayer.succeed(new Impl())
}
