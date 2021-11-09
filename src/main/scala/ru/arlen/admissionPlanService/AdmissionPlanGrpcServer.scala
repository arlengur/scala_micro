package ru.arlen.admissionPlanService

import `true`.admissionPlanService.{AbiturientRequest, AdmissionPlanResponse}
import io.grpc.Status
import ru.arlen.admissionPlanService.routes.AdmissionPlanAPI
import scalapb.zio_grpc.{ServerMain, ServiceList}
import zio.ZIO

object AdmissionPlanGrpcServer extends ServerMain {
  object ZioAbiturientServiceImpl extends AdmissionPlanAPI {
    override def checkAbiturientRequest(request: AbiturientRequest): ZIO[Any, Status, AdmissionPlanApp]
    ZIO.effect(AdmissionPlanResponse(1)).mapError(ex => Status.INTERNAL)
  }
  override def services: ServiceList[zio.ZEnv] = ServiceList.add(ZioAbiturientServiceImpl)
  override def port: Int = 3333
}
