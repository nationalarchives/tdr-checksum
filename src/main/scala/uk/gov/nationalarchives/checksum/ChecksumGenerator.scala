package uk.gov.nationalarchives.checksum

import java.io.{File, FileInputStream}
import java.security.MessageDigest
import java.util.UUID
import cats.effect.{IO, Resource}
import com.typesafe.config.{Config, ConfigFactory}
import uk.gov.nationalarchives.checksum.ChecksumGenerator.{ChecksumFile, getFilePath}

class ChecksumGenerator(configFactory: Config) {

  def generate(checksumFile: ChecksumFile): IO[String] = {
    val chunkSizeInMB = configFactory.getInt("chunk.size")
    val chunkSizeInBytes: Int = chunkSizeInMB * 1024 * 1024
    val messageDigester: MessageDigest = MessageDigest.getInstance("SHA-256")
    for {
      _ <- {
        Resource.fromAutoCloseable(IO(new FileInputStream(new File(getFilePath(checksumFile)))))
          .use(inStream => {
            val bytes = new Array[Byte](chunkSizeInBytes)
            IO(Iterator.continually(inStream.read(bytes)).takeWhile(_ != -1).foreach(messageDigester.update(bytes, 0, _)))
          })
      }
      checksum <- IO(messageDigester.digest)
      mapped <- IO(checksum.map(byte => f"$byte%02x").mkString)
    } yield mapped
  }
}

object ChecksumGenerator {
  case class ChecksumFile(consignmentId: UUID, fileId: UUID, originalPath: String, userId: UUID, s3SourceBucket: Option[String] = None, s3SourceBucketKey: Option[String] = None)

  case class ChecksumResult(checksum: Checksum)

  case class Checksum(fileId: UUID, sha256Checksum: String)

  val configFactory: Config = ConfigFactory.load

  def apply(): ChecksumGenerator = new ChecksumGenerator(configFactory)

  def getFilePath(checksumFile: ChecksumFile) = s"""${configFactory.getString("root.directory")}/${checksumFile.consignmentId}/${checksumFile.originalPath}"""
}
