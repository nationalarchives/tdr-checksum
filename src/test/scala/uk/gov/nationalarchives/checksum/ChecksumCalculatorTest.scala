package uk.gov.nationalarchives.checksum

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.nationalarchives.checksum.utils.TestUtils._
import io.circe.parser.decode
import graphql.codegen.AddFileMetadata.addFileMetadata.AddFileMetadata

import scala.util.Try

class ChecksumCalculatorTest extends AnyFlatSpec with BeforeAndAfterAll with BeforeAndAfterEach  {

  override def beforeAll(): Unit = {
    s3Api.start
    sqsApi.start()
    outputQueueHelper.createQueue
    inputQueueHelper.createQueue
  }

  override def beforeEach(): Unit = {
    createBucket
    outputQueueHelper.receive.foreach(outputQueueHelper.delete)
    inputQueueHelper.receive.foreach(inputQueueHelper.delete)
  }

  override def afterEach(): Unit = {
    deleteBucket()
  }

  "The update method" should "put a message in the output queue if the message is successful " in {
    putFile("ten_bytes")
    new ChecksumCalculator().update(createEvent("sns_s3_event"), null)
    val msgs = outputQueueHelper.receive
    msgs.size should equal(1)
  }

  "The update method" should "put one message in the output queue, delete the successful message and leave the key error message" in {
    putFile("ten_bytes")
    Try(new ChecksumCalculator().update(createEvent("sns_s3_event", "sns_s3_no_key"), null))
    val outputMessages = outputQueueHelper.receive
    val inputMessages = inputQueueHelper.receive
    outputMessages.size should equal(1)
    inputMessages.size should equal(1)
  }

  "The update method" should "put one message in the output queue, delete the successful message and leave the decoding error message" in {
    putFile("ten_bytes")
    Try(new ChecksumCalculator().update(createEvent("sns_s3_event", "sns_s3_invalid_json"), null))
    val outputMessages = outputQueueHelper.receive
    val inputMessages = inputQueueHelper.receive
    outputMessages.size should equal(1)
    inputMessages.size should equal(1)
  }

  "The update method" should "leave the queues unchanged if there are no successful messages" in {
    Try(new ChecksumCalculator().update(createEvent("sns_s3_invalid_json"), null))
    val outputMessages = outputQueueHelper.receive
    val inputMessages = inputQueueHelper.receive
    outputMessages.size should equal(0)
    inputMessages.size should equal(1)
  }

  "The update method" should "return the receipt handle for a successful message" in {
    putFile("ten_bytes")
    val event = createEvent("sns_s3_event")
    val response = new ChecksumCalculator().update(event, null)
    response(0) should equal(receiptHandle(event.getRecords.get(0).getBody))
  }

  "The update method" should "throw an exception for a no key error" in {
    val event = createEvent("sns_s3_no_key")
    val exception = intercept[RuntimeException] {
      new ChecksumCalculator().update(event, null)
    }
    exception.getMessage should equal("The resource you requested does not exist (Service: S3, Status Code: 404, Request ID: null, Extended Request ID: null)")
  }

  "The update method" should "calculate the correct checksum for a file with one chunk" in {
    putFile("ten_bytes")
    new ChecksumCalculator().update(createEvent("sns_s3_event"), null)
    val msgs = outputQueueHelper.receive
    val metadata: AddFileMetadata = decode[AddFileMetadata](msgs(0).body) match {
      case Right(metadata) => metadata
      case Left(error) => throw error
    }
    metadata.value should equal("9e994cad5c56b82e10bd9012e98992027631cd36daef4739613c5dd68b7d7f0e")
  }

  "The update method" should "calculate the correct checksum for a file with two chunks" in {
    putFile("more_than_one_meg")
    new ChecksumCalculator().update(createEvent("sns_s3_event_large_file"), null)
    val msgs = outputQueueHelper.receive
    val metadata: AddFileMetadata = decode[AddFileMetadata](msgs(0).body) match {
      case Right(metadata) => metadata
      case Left(error) => throw error
    }
    metadata.value should equal("c08c59a10f61526ae02808f761d2fd75c09cb2d77d608dc01fdbc35e3fdaf11d")
  }

}
