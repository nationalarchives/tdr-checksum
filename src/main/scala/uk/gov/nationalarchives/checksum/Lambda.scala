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

import java.io.{File, InputStream, OutputStream}
import java.nio.file.Paths
import scala.io.Source
import scala.language.postfixOps

class Lambda {
  val configFactory: Config = ConfigFactory.load
  private val defaultBucket = configFactory.getString("s3.bucket")

  private def s3BucketOverride(checksumFile: ChecksumFile): String = checksumFile.s3SourceBucket match {
    case Some(v) => v
    case _ => defaultBucket
  }

  private def s3ObjectKeyOverride(checksumFile: ChecksumFile): String = checksumFile.s3SourceBucketKey match {
    case Some(v) => v
    case _ => s"${checksumFile.userId}/${checksumFile.consignmentId}/${checksumFile.fileId}"
  }

  private def download(checksumFile: ChecksumFile): IO[Any] = {
    val s3Utils = S3Utils(s3Async(configFactory.getString("s3.endpoint")))
    val filePath = getFilePath(checksumFile)
    if(new File(filePath).exists()) {
      IO.unit
    } else {
      IO(new File(filePath.split("/").dropRight(1).mkString("/")).mkdirs()).flatMap(_ => {
        s3Utils.downloadFiles(s3BucketOverride(checksumFile), s3ObjectKeyOverride(checksumFile), Paths.get(filePath).some)
      })
    }
  }

  def process(input: InputStream, output: OutputStream): Unit = {
    val body = Source.fromInputStream(input).getLines().mkString
    for {
      checksumFile <- IO.fromEither(decode[ChecksumFile](body))
      _ <- download(checksumFile)
      checksum <- ChecksumGenerator().generate(checksumFile)
      output <- Resource.fromAutoCloseable(IO(output)).use(outputStream => {
        outputStream.write(ChecksumResult(Checksum(checksumFile.fileId, checksum)).asJson.printWith(Printer.noSpaces).getBytes())
        IO.unit
      })
    } yield output
  }.unsafeRunSync()
}
