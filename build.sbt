scalacOptions += "-Ymacro-annotations"

lazy val root = project
  .in(file("."))
  .settings(
    name         := "scala_micro",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := "2.13.3",
    libraryDependencies ++= Dependencies.http4sServer,
    libraryDependencies ++= Dependencies.pureconfig,
    libraryDependencies ++= Dependencies.circe,
    libraryDependencies ++= Dependencies.zio,
    libraryDependencies ++= Dependencies.zioConfig,
    libraryDependencies ++= Dependencies.grpc,
    libraryDependencies ++= Seq(
      Dependencies.logback
    ),
    addCompilerPlugin(Dependencies.kindProjector),
    Compile / PB.targets := Seq(
      scalapb.gen(grpc = true)          -> (Compile / sourceManaged).value / "scalapb",
      scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value / "scalapb"
    )
  )
