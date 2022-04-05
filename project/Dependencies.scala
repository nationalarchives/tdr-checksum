import sbt._

object Dependencies {
  lazy val lambdaJavaCore = "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
  lazy val lambdaJavaEvents = "com.amazonaws" % "aws-lambda-java-events" % "3.11.0"
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.232"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.2"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.11"
  lazy val elasticMq = "org.elasticmq" %% "elasticmq-server" % "1.3.6"
  lazy val elasticMqSqs = "org.elasticmq" %% "elasticmq-rest-sqs" % "1.3.6"
  lazy val circeCore = "io.circe" %% "circe-core" % "0.14.1"
  lazy val circeGeneric = "io.circe" %% "circe-generic" % "0.14.1"
  lazy val circeParser = "io.circe" %% "circe-parser" % "0.14.1"
  lazy val awsUtils =  "uk.gov.nationalarchives" %% "tdr-aws-utils" % "0.1.27"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.3.10"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.11"
  lazy val logstashLogbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.0.1"
  lazy val wiremock = "com.github.tomakehurst" % "wiremock" % "2.27.2"
}
