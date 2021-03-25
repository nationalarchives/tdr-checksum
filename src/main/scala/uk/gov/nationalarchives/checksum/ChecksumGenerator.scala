package uk.gov.nationalarchives.checksum

import java.io.{File, FileInputStream}
import java.security.MessageDigest
import java.util.UUID

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import uk.gov.nationalarchives.checksum.ChecksumGenerator.ChecksumFile

class ChecksumGenerator(config: Map[String, String]) {

  def generate(checksumFile: ChecksumFile): IO[String] = {
    val chunkSizeInMB = config("chunk.size").toInt
    val chunkSizeInBytes: Int = chunkSizeInMB * 1024 * 1024
    val filePath = s"""${config("efs.root.location")}/${checksumFile.consignmentId}/${checksumFile.originalPath}"""
    val messageDigester: MessageDigest = MessageDigest.getInstance("SHA-256")

    for {
      _ <- {
        Resource.fromAutoCloseable(IO(new FileInputStream(new File(filePath))))
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
  case class ChecksumFile(consignmentId: UUID, fileId: UUID, originalPath: String)
  def apply(config: Map[String, String]): ChecksumGenerator = new ChecksumGenerator(config)
}
