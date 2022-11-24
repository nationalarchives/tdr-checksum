package uk.gov.nationalarchives.checksum

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import uk.gov.nationalarchives.checksum.ChecksumGenerator.{Checksum, ChecksumFile, ChecksumResult, getFilePath}
import uk.gov.nationalarchives.aws.utils.s3.S3Clients._
import uk.gov.nationalarchives.aws.utils.s3.S3Utils

import java.io.{InputStream, OutputStream}
import java.nio.file.Paths
import scala.io.Source
import scala.language.postfixOps

class Lambda {
  def key(checksumFile: ChecksumFile) = s"${checksumFile.userId}/${checksumFile.consignmentId}/${checksumFile.fileId}"
  val configFactory: Config = ConfigFactory.load

  def process(input: InputStream, output: OutputStream): Unit = {
    val body = Source.fromInputStream(input).getLines().mkString
    val bucket = configFactory.getString("s3.bucket")

    val s3Utils = S3Utils(s3Async(configFactory.getString("s3.endpoint")))
    for {
      checksumFile <- IO.fromEither(decode[ChecksumFile](body))
      _ <- s3Utils.downloadFiles(bucket, key(checksumFile), Paths.get(getFilePath(checksumFile)).some)
      checksum <- ChecksumGenerator().generate(checksumFile)
      output <- Resource.fromAutoCloseable(IO(output)).use(outputStream => {
        outputStream.write(ChecksumResult(Checksum(checksumFile.fileId, checksum)).asJson.printWith(Printer.noSpaces).getBytes())
        IO.unit
      })
    } yield output
  }.unsafeRunSync()
}
