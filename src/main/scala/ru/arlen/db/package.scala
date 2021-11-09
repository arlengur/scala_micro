package ru.arlen

import zio.Has
import doobie.util.transactor.Transactor
import liquibase.Liquibase
import zio.Task
import zio.RIO
import zio.ZManaged
import ru.arlen.configuration.Config
import zio.ZLayer
import ru.arlen.configuration.Configuration
import zio.URIO
import zio.ZIO
import zio.interop.catz._
import liquibase.resource.FileSystemResourceAccessor
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.CompositeResourceAccessor
import liquibase.database.jvm.JdbcConnection
import doobie.quill.DoobieContext
import io.getquill.NamingStrategy
import io.getquill.Escape
import io.getquill.Literal
import scala.concurrent.ExecutionContext
import zio._
import doobie.hikari.HikariTransactor
import cats.effect.Blocker
import zio.blocking.Blocking
import zio.macros.accessible

package object db {
  type DBTransactor = Has[Transactor[Task]]

  type LiquibaseService = Has[LiquibaseService.Service]

  type Liqui = Has[Liquibase]

  // Сервис для миграции, использует DBTransactor, чтобы проводить миграции
  @accessible
  object LiquibaseService {

    trait Service {
      def performMigration: RIO[Liqui, Unit]
    }

    class Impl extends Service {

      override def performMigration: RIO[Liqui, Unit] = liquibase.map(_.update("dev"))
    }

    // запуск сервиса liquibase, transactor нужен для получения соединения
    // ZManaged так как тут происходит взаимодействие с ресурсами
    def mkLiquibase(config: Config, transactor: Transactor[Task]): ZManaged[Any, Throwable, Liquibase] = for {
      // ресурс из cats-effect который привели к зио и к ZManaged при помощи ZManaged из interop
      connection <- transactor.connect(transactor.kernel).toManagedZIO
      fileAccessor <- ZIO.effect(new FileSystemResourceAccessor()).toManaged_
      classLoader <- ZIO.effect(classOf[LiquibaseService].getClassLoader).toManaged_
      classLoaderAccessor <- ZIO.effect(new ClassLoaderResourceAccessor(classLoader)).toManaged_
      // так как наша миграция это файлы на диске которые нужно читать
      fileOpener <- ZIO.effect(new CompositeResourceAccessor(fileAccessor, classLoaderAccessor)).toManaged_
      jdbcConn <- ZManaged.makeEffect(new JdbcConnection(connection))(c => c.close())
      liqui <- ZIO.effect(new Liquibase(config.liquibase.changeLog, fileOpener, jdbcConn)).toManaged_
    } yield liqui


    val liquibaseLayer: ZLayer[DBTransactor with Configuration, Throwable, Liqui] = ZLayer.fromManaged(
      for {
        config <- zio.config.getConfig[Config].toManaged_
        transactor <- DBTransactor.dbTransactor.toManaged_
        liquibase <- mkLiquibase(config, transactor)
      } yield (liquibase)
    )

    def liquibase: URIO[Liqui, Liquibase] = ZIO.service[Liquibase]

    val live: ULayer[LiquibaseService] = ZLayer.succeed(new Impl)

  }

  // соединяется с БД и выполняет запросы
  object DBTransactor {

    // дает доступ к нужным нам структурам данных
    val doobieContext = new DoobieContext.Postgres(NamingStrategy(Escape, Literal)) // Literal naming scheme like camel case

    // HikariTransactor из doobie создает трензактор за которым стоит HikariPool
    // connectEC для получения соединений
    // transactEC для выполнения транзакций
    def mkTransactor(conf: configuration.DbConfig, connectEC: ExecutionContext, transactEC: ExecutionContext): Managed[Throwable, Transactor[Task]] =
      HikariTransactor.newHikariTransactor[Task](
        conf.driver,
        conf.url,
        conf.user,
        conf.password,
        connectEC,
        Blocker.liftExecutionContext(transactEC)
      ).toManagedZIO

    val live: ZLayer[Configuration with Blocking, Throwable, DBTransactor] = ZLayer.fromManaged(
      (for {
        config <- zio.config.getConfig[Config].toManaged_
        // с помощью ZIO.descriptor получаем доступ к контексту файбера, в котором выполняется код
        ec <- ZIO.descriptor.map(_.executor.asEC).toManaged_
        blocingEC <- zio.blocking.blockingExecutor.map(_.asEC).toManaged_
        transactor <- DBTransactor.mkTransactor(config.db, ec, blocingEC)
      } yield transactor)
    )

    def dbTransactor: URIO[DBTransactor, Transactor[Task]] = ZIO.service[Transactor[Task]]
  }
}

