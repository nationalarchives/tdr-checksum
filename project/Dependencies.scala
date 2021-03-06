import sbt._

object Dependencies {
  lazy val lambdaJavaCore = "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
  lazy val lambdaJavaEvents = "com.amazonaws" % "aws-lambda-java-events" % "3.1.0"
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.48"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.0"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.1.1"
  lazy val elasticMq = "org.elasticmq" %% "elasticmq-server" % "1.1.1"
  lazy val elasticMqSqs = "org.elasticmq" %% "elasticmq-rest-sqs" % "1.1.1"
  lazy val circeCore = "io.circe" %% "circe-core" % "0.13.0"
  lazy val circeGeneric = "io.circe" %% "circe-generic" % "0.13.0"
  lazy val circeParser = "io.circe" %% "circe-parser" % "0.13.0"
  lazy val awsUtils =  "uk.gov.nationalarchives.aws.utils" %% "tdr-aws-utils" % "0.1.15"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "2.2.0"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  lazy val wiremock = "com.github.tomakehurst" % "wiremock" % "2.27.2"
}
