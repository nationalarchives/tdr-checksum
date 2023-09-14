import Dependencies._

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "uk.gov.nationalarchives"
ThisBuild / organizationName := "checksum-calculator"

libraryDependencies ++= Seq(
  typesafe,
  circeCore,
  circeGeneric,
  circeParser,
  catsEffect,
  s3Utils,
  scalaTest % Test,
  wiremock % Test
)

(assembly / assemblyMergeStrategy) := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

(assembly / assemblyJarName) := "checksum.jar"

Test / fork := true
Test / envVars := Map("AWS_ACCESS_KEY_ID" -> "test", "AWS_SECRET_ACCESS_KEY" -> "test")
