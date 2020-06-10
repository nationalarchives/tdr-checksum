package uk.gov.nationalarchives.checksum.utils

import java.io.File
import java.net.URI
import java.util.Base64

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import io.findify.s3mock.S3Mock
import io.findify.sqsmock.SQSService
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, CreateBucketResponse, DeleteBucketRequest, DeleteBucketResponse, PutObjectRequest, PutObjectResponse}
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, CreateQueueResponse, DeleteMessageRequest, DeleteMessageResponse, Message, ReceiveMessageRequest, SendMessageRequest, SendMessageResponse}

import scala.io.Source.fromResource
import scala.jdk.CollectionConverters._
object TestUtils {

  def createBucket: CreateBucketResponse = s3Client.createBucket(CreateBucketRequest.builder.bucket("testbucket").build)
  def deleteBucket(): DeleteBucketResponse = s3Client.deleteBucket(DeleteBucketRequest.builder.bucket("testbucket").build)

  def putFile(location: String): PutObjectResponse = {
    val path = new File(getClass.getResource(s"/testfiles/$location").getPath).toPath
    val putObjectRequest = PutObjectRequest.builder.bucket("testbucket").key("consignmentId/acea5919-25a3-4c6b-8908-fa47cc77878f").build
    s3Client.putObject(putObjectRequest, path)
  }

  def receiptHandle(body: String): String = Base64.getEncoder.encodeToString(body.getBytes("UTF-8"))

  def createEvent(locations: String*): SQSEvent = {
    val event = new SQSEvent()

    val records = locations.map(location => {
      val record = new SQSMessage()
      val body = fromResource(s"json/$location.json").mkString
      record.setBody(body)
      inputQueueHelper.send(body)
      record.setReceiptHandle(receiptHandle(body))
      record
    })

    event.setRecords(records.asJava)
    event
  }

  case class QueueHelper(queueUrl: String) {
    val sqsClient: SqsClient = SqsClient.builder()
      .region(Region.EU_WEST_2)
      .endpointOverride(URI.create("http://localhost:8002"))
      .build()

    def send(body: String): SendMessageResponse = sqsClient.sendMessage(SendMessageRequest
      .builder.messageBody(body).queueUrl(queueUrl).build())

    def receive: List[Message] = sqsClient.receiveMessage(ReceiveMessageRequest
      .builder
      .maxNumberOfMessages(10)
      .queueUrl(queueUrl)
      .build).messages.asScala.toList

    def createQueue: CreateQueueResponse = sqsClient.createQueue(CreateQueueRequest.builder.queueName(queueUrl.split("/")(4)).build())

    def delete(msg: Message): DeleteMessageResponse = sqsClient.deleteMessage(DeleteMessageRequest
      .builder.queueUrl(queueUrl).receiptHandle(msg.receiptHandle()).build)
  }

  val port = 8002
  val account = 1
  val inputQueueName = "test_input_queue"
  val outputQueueName = "test_output_queue"
  val sqsApi = new SQSService(port)
  val inputQueueUrl = s"http://localhost:$port/$account/$inputQueueName"
  val outputQueueUrl = s"http://localhost:$port/$account/$outputQueueName"
  val s3Api = S3Mock(port = 8003, dir = "/tmp/s3")

  val s3Client: S3Client = S3Client.builder
    .region(Region.EU_WEST_2)
    .endpointOverride(URI.create("http://localhost:8003/"))
    .build()

  val inputQueueHelper = QueueHelper(inputQueueUrl)
  val outputQueueHelper = QueueHelper(outputQueueUrl)
}
