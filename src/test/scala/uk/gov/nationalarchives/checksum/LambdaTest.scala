package uk.gov.nationalarchives.checksum

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.circe.generic.auto._
import io.circe.parser.decode
import org.apache.commons.io.output.ByteArrayOutputStream
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.nationalarchives.checksum.ChecksumGenerator.ChecksumResult

import java.io.{ByteArrayInputStream, File}
import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.io.Source.fromResource
import scala.reflect.io.Directory

class LambdaTest extends AnyFlatSpec with BeforeAndAfterAll with BeforeAndAfterEach  {
  val wiremockS3 = new WireMockServer(8003)
  val consignmentId = "f0a73877-6057-4bbb-a1eb-7c7b73cab586"
  val fileId = "acea5919-25a3-4c6b-8908-fa47cc77878f"
  val fileUuid: UUID = UUID.fromString(fileId)
  val expectedChecksum = "be776ad8d02e9fa4c35484877b2d96753a847e8bfc59c917c2442f3746850fb5"

  override def beforeAll(): Unit = {
    wiremockS3.start()
  }

  override def afterAll(): Unit = {
    wiremockS3.stop()
  }

  override def beforeEach(): Unit = {
    wiremockS3.resetAll()
  }

  override def afterEach(): Unit = {
    val runningFiles = new File(s"./src/test/resources/testfiles/running-files/")
    if (runningFiles.exists()) {
      new Directory(runningFiles).deleteRecursively()
    }
  }

  def mockS3Response(fileName: String, key: Option[String] = None): StubMapping = {
    val urlStub = key match {
      case Some(v) => s"/$v"
      case _ => s"/bd4cbe2e-b752-4432-8aec-a3234b9d4339/$consignmentId/$fileId"
    }
    val filePath = getClass.getResource(s"/testfiles/$consignmentId/$fileName").getFile
    val bytes = Files.readAllBytes(Paths.get(filePath))
    wiremockS3.stubFor(get(urlEqualTo(urlStub))
      .willReturn(aResponse().withStatus(200).withBody(bytes))
    )
  }

  def createEvent(location: String): ByteArrayInputStream = {
    new ByteArrayInputStream(fromResource(s"json/$location.json").mkString.getBytes())
  }

  "The process method" should "return the correct file ID and checksum from the default S3 bucket and key" in {
    val outputStream = new ByteArrayOutputStream()
    mockS3Response("ten_bytes")
    new Lambda().process(createEvent("file_event"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[ChecksumResult](result).toOption
    decoded.isDefined should be(true)
    decoded.get.checksum.sha256Checksum should equal(expectedChecksum)
    decoded.get.checksum.fileId should equal(fileUuid)
  }

  "The process method" should "return the correct file ID and checksum from the specified S3 bucket and key" in {
    val outputStream = new ByteArrayOutputStream()
    mockS3Response("ten_bytes", Some(s"bucket/key/$fileId"))
    new Lambda().process(createEvent("file_event_source_details"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[ChecksumResult](result).toOption
    decoded.isDefined should be(true)
    decoded.get.checksum.sha256Checksum should equal(expectedChecksum)
    decoded.get.checksum.fileId should equal(fileUuid)
  }

  "The process method" should "succeed if the file already exists" in {
    val outputStream = new ByteArrayOutputStream()
    val basePath = s"./src/test/resources/testfiles/running-files/$consignmentId/"
    val filePath = s"$basePath/ten_bytes"
    new File(basePath).mkdirs()
    Files.copy(Paths.get(s"./src/test/resources/testfiles/$consignmentId/ten_bytes"), Paths.get(filePath))
    mockS3Response("ten_bytes")
    new Lambda().process(createEvent("file_event"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[ChecksumResult](result).toOption
    decoded.isDefined should be(true)
    decoded.get.checksum.sha256Checksum should equal(expectedChecksum)
    decoded.get.checksum.fileId should equal(fileUuid)
  }

   "The process method" should "throw an exception if the file is not found in S3" in {
    val event = createEvent("file_no_key")
    val exception = intercept[RuntimeException] {
      new Lambda().process(event, null)
    }
    exception.getMessage should equal("software.amazon.awssdk.services.s3.model.S3Exception: (Service: S3, Status Code: 404, Request ID: null) (SDK Attempt Count: 1)")
  }

  "The update method" should "calculate the correct checksum for a file with one chunk" in {
    mockS3Response("ten_bytes")
    val outputStream = new ByteArrayOutputStream()
    new Lambda().process(createEvent("file_event"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    decode[ChecksumResult](result).toOption.get.checksum.sha256Checksum should equal(expectedChecksum)
  }

  "The update method" should "calculate the correct checksum for a file with two chunks" in {
    mockS3Response("more_than_one_meg")
    val outputStream = new ByteArrayOutputStream()
    new Lambda().process(createEvent("file_event_large_file"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    decode[ChecksumResult](result).toOption.get.checksum.sha256Checksum should equal("c08c59a10f61526ae02808f761d2fd75c09cb2d77d608dc01fdbc35e3fdaf11d")
  }
}
