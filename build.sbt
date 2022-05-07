val scala3Version = "2.13.4"
lazy val akkaHttpVersion = "10.2.9"
lazy val akkaVersion = "2.6.19"
lazy val circeVersion = "0.14.1"
lazy val root = project
  .in(file("."))
  .settings(
    name := "akka-cassandra-bank",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
      "com.datastax.oss" % "java-driver-core" % "4.14.1", // See https://github.com/akka/alpakka/issues/2556
      "com.typesafe.akka" %% "akka-persistence-cassandra" % "1.0.5",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "de.heikoseeberger" %% "akka-http-circe" % "1.39.2",
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.12" % Test
    )
  )
