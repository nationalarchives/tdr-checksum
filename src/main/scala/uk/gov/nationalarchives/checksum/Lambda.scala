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
  private val cleanBucket = configFactory.getString("s3.cleanBucket")
  private val dirtyBucket = configFactory.getString("s3.bucket")
  private case class S3Location(s3Bucket: String, s3ObjectKey: String)

  private def s3Location(checksumFile: ChecksumFile): S3Location = checksumFile.s3SourceBucket match {
    case Some(v) if v == cleanBucket => S3Location(v, s"${checksumFile.consignmentId}/${checksumFile.fileId}")
    case _ => S3Location(dirtyBucket, s"${checksumFile.userId}/${checksumFile.consignmentId}/${checksumFile.fileId}")
  }

  private def download(checksumFile: ChecksumFile): IO[Any] = {
    val s3 = s3Location(checksumFile)
    val s3Utils = S3Utils(s3Async(configFactory.getString("s3.endpoint")))
    val filePath = getFilePath(checksumFile)
    if(new File(filePath).exists()) {
      IO.unit
    } else {
      IO(new File(filePath.split("/").dropRight(1).mkString("/")).mkdirs()).flatMap(_ => {
        s3Utils.downloadFiles(s3.s3Bucket, s3.s3ObjectKey, Paths.get(filePath).some)
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
