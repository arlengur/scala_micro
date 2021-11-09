package ru.arlen.admissionPlanService

import pureconfig.ConfigSource
import zio.{Task, ULayer, ZIO, ZLayer}

package object configuration {
  case class Config(admissionPlanApi: ApiConfig)
  case class ApiConfig(host: String, port: Int, baseApiUrl: String)

  type Configuration = zio.Has[Config]

  object Configuration {
    trait Service {
      def load: Task[Config]
    }

    trait Live extends Configuration.Service {
      val load: Task[Config] = Task.effect(ConfigSource.default.loadOrThrow[Config])
    }

    val live: ULayer[Configuration] = ZLayer.succeed(new Live {})
  }

  // accessible pattern
  val load: ZIO[Configuration, Throwable, Config] = ZIO.accessM(_.get.load)
}
