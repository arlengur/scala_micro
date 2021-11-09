package ru.arlen.admissionPlanService

package object services {
  sealed trait AdmissionPlanResponse
  object AdmissionPlanResponse {
    def accepted(id: Int): AdmissionPlanResponse = AbiturientRequestAccepted(id)
    case class AbiturientRequestAccepted(id: Int) extends AdmissionPlanResponse

    object AbiturientRequestAccepted {
    }

    case class AbiturientRequestRejected(code: String) extends AdmissionPlanResponse
    object AbiturientRequestRejected {
    }
  }
}
