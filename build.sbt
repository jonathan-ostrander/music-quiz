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
      "net.katsstuff" %% "ackcord" % "0.17.0",
      "io.spray" %%  "spray-json" % "1.3.5",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.apache.commons" % "commons-text" % "1.9",
      "io.getquill" %% "quill-async-postgres" % "3.5.2",
      scalaTest % Test,
    ),
    assemblyMergeStrategy in assembly := {
      case PathList("reference.conf") => MergeStrategy.concat
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case x => MergeStrategy.first
    },
  )
