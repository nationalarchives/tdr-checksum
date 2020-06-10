import Dependencies._

ThisBuild / scalaVersion := "2.13.2"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "uk.gov.nationalarchives"
ThisBuild / organizationName := "checksum-calculator"

libraryDependencies ++= Seq(
  sqsSdk,
  s3Sdk,
  lambdaJavaCore,
  lambdaJavaEvents,
  generatedGraphql,
  typesafe,
  circeCore,
  circeGeneric,
  circeParser,
  scalaTest % Test,
  sqsMock % Test,
  s3Mock % Test,
  mockito % Test
)

resolvers += "TDR Releases" at "s3://tdr-releases-mgmt"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
