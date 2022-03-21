import Dependencies._

ThisBuild / scalaVersion := "2.13.8"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "uk.gov.nationalarchives"
ThisBuild / organizationName := "checksum-calculator"

libraryDependencies ++= Seq(
  lambdaJavaCore,
  lambdaJavaEvents,
  awsUtils,
  generatedGraphql,
  typesafe,
  circeCore,
  circeGeneric,
  circeParser,
  catsEffect,
  scalaLogging,
  logback,
  logstashLogbackEncoder,
  scalaTest % Test,
  elasticMq % Test,
  elasticMqSqs % Test,
  wiremock % Test
)

resolvers += "TDR Releases" at "s3://tdr-releases-mgmt"

(assembly / assemblyMergeStrategy) := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

(assembly / assemblyJarName) := "checksum.jar"