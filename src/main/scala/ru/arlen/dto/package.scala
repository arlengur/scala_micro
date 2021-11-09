package ru.arlen

import ru.arlen.dao.entities.PhoneRecord

package object dto {
  case class PhoneRecordDTO(phone: String, fio: String)

  object PhoneRecordDTO{
    def from(phoneRecord: PhoneRecord): PhoneRecordDTO = PhoneRecordDTO(
      phoneRecord.phone,
      phoneRecord.fio
    )
  }
}
