package uk.gov.nationalarchives.checksum

import com.github.tomakehurst.wiremock.client.WireMock.{post, urlEqualTo}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.nationalarchives.checksum.utils.TestUtils._
import io.circe.parser.decode
import graphql.codegen.AddFileMetadata.addFileMetadata.AddFileMetadata

import scala.util.Try

class LambdaTest extends AnyFlatSpec with BeforeAndAfterAll with BeforeAndAfterEach  {

  override def beforeAll(): Unit = {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
  }

  override def afterAll(): Unit = {
    wiremockKmsEndpoint.stop()
  }

  override def beforeEach(): Unit = {
    outputQueueHelper.createQueue
    inputQueueHelper.createQueue
  }

  override def afterEach(): Unit = {
    outputQueueHelper.deleteQueue()
    inputQueueHelper.deleteQueue()
  }

  "The update method" should "put a message in the output queue if the message is successful " in {
    new Lambda().process(createEvent("sqs_file_event"), null)
    val msgs = outputQueueHelper.receive
    msgs.size should equal(1)
  }

  "The update method" should "put one message in the output queue, delete the successful message and leave the key error message" in {
    Try(new Lambda().process(createEvent("sqs_file_event", "sqs_file_no_key"), null))
    val outputMessages = outputQueueHelper.receive
    val inputMessages = inputQueueHelper.receive
    outputMessages.size should equal(1)
    inputMessages.size should equal(1)
  }

  "The update method" should "put one message in the output queue, delete the successful message and leave the decoding error message" in {
    Try(new Lambda().process(createEvent("sqs_file_event", "sqs_file_invalid_json"), null))
    val outputMessages = outputQueueHelper.receive
    outputMessages.size should equal(1)
    inputQueueHelper.nonVisibleMessageCount should equal(1)
  }

  "The update method" should "leave the queues unchanged if there are no successful messages" in {
    Try(new Lambda().process(createEvent("sqs_file_invalid_json"), null))
    outputQueueHelper.receive.size should equal(0)
    inputQueueHelper.nonVisibleMessageCount should equal(1)
  }

  "The update method" should "return the receipt handle for a successful message" in {
    val event = createEvent("sqs_file_event")
    val receiptHandle = event.getRecords.get(0).getReceiptHandle
    val response = new Lambda().process(event, null)
    //Receipt handle for the same message is in the form <static_uuid>#<variable_uuid> so we need to check the first uuid
    response.head.split("#")(0) should equal(receiptHandle.split("#")(0))
  }

  "The update method" should "throw an exception for a no key error" in {
    val event = createEvent("sqs_file_no_key")
    val exception = intercept[RuntimeException] {
      new Lambda().process(event, null)
    }
    exception.getMessage should equal("java.io.FileNotFoundException: ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586/no_file (No such file or directory)")
  }

  "The update method" should "calculate the correct checksum for a file with one chunk" in {
    new Lambda().process(createEvent("sqs_file_event"), null)
    val msgs = outputQueueHelper.receive
    val metadata: AddFileMetadata = decode[AddFileMetadata](msgs.head.body) match {
      case Right(metadata) => metadata
      case Left(error) => throw error
    }
    metadata.value should equal("9e994cad5c56b82e10bd9012e98992027631cd36daef4739613c5dd68b7d7f0e")
  }

  "The update method" should "calculate the correct checksum for a file with two chunks" in {
    new Lambda().process(createEvent("sqs_file_event_large_file"), null)
    val msgs = outputQueueHelper.receive
    val metadata: AddFileMetadata = decode[AddFileMetadata](msgs.head.body) match {
      case Right(metadata) => metadata
      case Left(error) => throw error
    }
    metadata.value should equal("c08c59a10f61526ae02808f761d2fd75c09cb2d77d608dc01fdbc35e3fdaf11d")
  }

  "The update method" should "leave an invalid json message in the input queue in a non visible state" in {
    Try(new Lambda().process(createEvent("sqs_file_invalid_json"), null))
    inputQueueHelper.nonVisibleMessageCount should equal(1)
  }

  "The update method" should "make the message immediately available for retry" in {
    Try(new Lambda().process(createEvent("sqs_file_no_key"), null))
    inputQueueHelper.receive.size should equal(1)
  }
}
