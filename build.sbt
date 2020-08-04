import Dependencies._

ThisBuild / scalaVersion     := "2.13.2"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "dev.ostrander"
ThisBuild / organizationName := "Ostrander"

lazy val root = (project in file("."))
  .settings(
    name := "music-quiz",
    resolvers += Resolver.JCenterRepository,
    scalacOptions ++= List(
      "-Xfatal-warnings",
      "-Xlint:unused",
    ),
    scalacOptions in (Compile, console) ~= (_.filter(_ => false)),
    libraryDependencies ++= List(   
      "net.katsstuff" %% "ackcord" % "0.16.1",
      "io.spray" %%  "spray-json" % "1.3.5",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.apache.commons" % "commons-text" % "1.9",
      scalaTest % Test,
    )
  )
