import Dependencies._

ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(
    name := "Flashbot Market Data Dashboard",
    resolvers += Resolver.bintrayRepo("infixtrading", "flashbot"),
    libraryDependencies ++= Seq(
      scalaTest % Test,
      "com.infixtrading" %% "flashbot-client" % "0.1.0-SNAPSHOT",
      "com.infixtrading" %% "flashbot-server" % "0.1.0-SNAPSHOT"
    ),
    fork in run := true
  )