package ru.arlen.services

import ru.arlen.dao.entities.PhoneRecord
import ru.arlen.dao.repositiories.PhoneRecordRepository
import ru.arlen.db.DBTransactor
import ru.arlen.dto.PhoneRecordDTO
import zio.{Has, RIO, ZIO, ZLayer}
import zio.interop.catz._
import zio.macros.accessible
import zio.random.Random

@accessible
object PhoneBookService {

  type PhoneBookService = Has[Service]

  trait Service {
    def list(): ZIO[DBTransactor, Throwable, List[PhoneRecordDTO]]
    def find(phone: String): ZIO[DBTransactor, Option[Throwable], (String, PhoneRecordDTO)]
    def insert(phoneRecord: PhoneRecordDTO): RIO[DBTransactor with Random, String]
    def update(id: String, phoneRecord: PhoneRecordDTO): RIO[DBTransactor, Unit]
    def delete(id: String): RIO[DBTransactor, Unit]
  }

  class Impl(phoneRecordRepository: PhoneRecordRepository.Service) extends Service {
    import doobie.implicits._
    def list(): ZIO[DBTransactor, Throwable, List[PhoneRecordDTO]] = for {
      transactor <- DBTransactor.dbTransactor
      result     <- phoneRecordRepository.list().transact(transactor)
    } yield result.map(PhoneRecordDTO.from)

    def find(phone: String): ZIO[DBTransactor, Option[Throwable], (String, PhoneRecordDTO)] = for {
      transactor <- DBTransactor.dbTransactor
      result     <- phoneRecordRepository.find(phone).transact(transactor).some
    } yield (result.id, PhoneRecordDTO.from(result))

    def insert(phoneRecord: PhoneRecordDTO): RIO[DBTransactor with Random, String] = for {
      transactor <- DBTransactor.dbTransactor
      uuid       <- zio.random.nextUUID.map(_.toString())
      _          <- phoneRecordRepository.insert(PhoneRecord(uuid, phoneRecord.phone, phoneRecord.fio)).transact(transactor)
    } yield uuid

    def update(id: String, phoneRecord: PhoneRecordDTO): RIO[DBTransactor, Unit] = for {
      transactor <- DBTransactor.dbTransactor
      _          <- phoneRecordRepository.update(PhoneRecord(id, phoneRecord.phone, phoneRecord.fio)).transact(transactor)
    } yield ()

    def delete(id: String): RIO[DBTransactor, Unit] = for {
      transactor <- DBTransactor.dbTransactor
      _          <- phoneRecordRepository.delete(id).transact(transactor)
    } yield ()
  }

  val live: ZLayer[PhoneRecordRepository.PhoneRecordRepository, Nothing, PhoneBookService.PhoneBookService] =
    ZLayer.fromService[PhoneRecordRepository.Service, PhoneBookService.Service](repo => new Impl(repo))
}
