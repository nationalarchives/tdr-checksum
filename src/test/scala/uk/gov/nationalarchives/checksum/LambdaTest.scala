package uk.gov.nationalarchives.checksum

import com.github.tomakehurst.wiremock.client.WireMock.{post, urlEqualTo}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.nationalarchives.checksum.utils.TestUtils._

import scala.util.Try

class LambdaTest extends AnyFlatSpec with BeforeAndAfterAll with BeforeAndAfterEach {

  override def beforeAll(): Unit = {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
    wiremockSqsEndpoint.start()
  }

  override def afterAll(): Unit = {
    wiremockKmsEndpoint.stop()
    wiremockSqsEndpoint.stop()
  }

  override def beforeEach(): Unit = {
    wiremockSqsEndpoint.resetRequests()
  }

  "The update method" should "put a message in the output queue if the message is successful " in {
    stubSendMessage()
    new Lambda().process(createEvent("sqs_file_event"), null)
    val msgs = getEventsForFilters(("QueueUrl", outputQueue), ("Action", "SendMessage"))
    msgs.size should equal(1)
  }

  "The update method" should "put one message in the output queue, delete the successful message and leave the key error message" in {
    stubSendMessage()
    stubDeleteMessage()
    Try(new Lambda().process(createEvent("sqs_file_event", "sqs_file_no_key"), null))
    outputSentMessageCount should equal(1)
    inputDeleteMessageCount should equal(1)
  }

    "The update method" should "put one message in the output queue, delete the successful message and leave the decoding error message" in {
      stubSendMessage()
      stubDeleteMessage()
      Try(new Lambda().process(createEvent("sqs_file_event", "sqs_file_invalid_json"), null))
      outputSentMessageCount should equal(1)
      inputDeleteMessageCount should equal(1)
    }

    "The update method" should "leave the queues unchanged if there are no successful messages" in {
      Try(new Lambda().process(createEvent("sqs_file_invalid_json"), null))
      outputSentMessageCount should equal(0)
      inputDeleteMessageCount should equal(0)
    }

    "The update method" should "return the receipt handle for a successful message" in {
      stubSendMessage()
      val event = createEvent("sqs_file_event")
      val response = new Lambda().process(event, null)
      response.head should equal(receiptHandle(event.getRecords.get(0).getBody))
    }

    "The update method" should "throw an exception for a no key error" in {
      val event = createEvent("sqs_file_no_key")
      val exception = intercept[RuntimeException] {
        new Lambda().process(event, null)
      }
      exception.getMessage should equal("java.io.FileNotFoundException: ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586/no_file (No such file or directory)")
    }

    "The update method" should "calculate the correct checksum for a file with one chunk" in {
      stubSendMessage()
      new Lambda().process(createEvent("sqs_file_event"), null)
      val expectedResponse = urlEncodeFile("file_event_expected_response")
      val outputQueueMessages = getEventsForFilters(("QueueUrl", outputQueue), ("Action", "SendMessage"), ("MessageBody", expectedResponse))
      outputQueueMessages.size should equal(1)
    }

    "The update method" should "calculate the correct checksum for a file with two chunks" in {
      stubSendMessage("dbb12b25d77563d8c8eb9191eae71511")
      new Lambda().process(createEvent("sqs_file_event_large_file"), null)
      val expectedResponse = urlEncodeFile("large_file_event_expected_response")
      val outputQueueMessages = getEventsForFilters(("QueueUrl", outputQueue), ("Action", "SendMessage"), ("MessageBody", expectedResponse))
      outputQueueMessages.size should equal(1)
    }

}
