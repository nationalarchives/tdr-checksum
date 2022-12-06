package uk.gov.nationalarchives.checksum

import io.circe.generic.auto._
import io.circe.parser.decode
import uk.gov.nationalarchives.checksum.ChecksumGenerator.ChecksumResult

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

object LambdaRunner extends App {
  val consignmentId = "907d547d-a9ed-45f3-b93c-055fc792a299"
  val fileId = "b634337e-5a63-42f4-b97e-4fb9d1c1d366"
  val originalPath = "testfiles/deliberately_unidentifiable_file.dat"
  val userId = "99fba346-3359-4ab7-9339-2a3a16053c9b"
  val body = s"""{"consignmentId": "$consignmentId", "fileId": "$fileId", "originalPath": "$originalPath", "userId": "$userId"}"""
  val baos = new ByteArrayInputStream(body.getBytes())
  val output = new ByteArrayOutputStream()
  new Lambda().process(baos, output)
  val result = decode[ChecksumResult](output.toByteArray.map(_.toChar).mkString).toOption.get
  println(result.checksum.fileId)
  println(result.checksum.sha256Checksum)
}

