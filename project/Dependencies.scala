import sbt._

object Dependencies {
  lazy val typesafe = "com.typesafe" % "config" % "1.4.3"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19"
  lazy val circeCore = "io.circe" %% "circe-core" % "0.14.10"
  lazy val circeGeneric = "io.circe" %% "circe-generic" % "0.14.10"
  lazy val circeParser = "io.circe" %% "circe-parser" % "0.14.10"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.4"
  lazy val s3Utils = "uk.gov.nationalarchives" %% "s3-utils" % "0.1.215"
  lazy val wiremock = "com.github.tomakehurst" % "wiremock" % "3.0.1"
}
