package uk.gov.nationalarchives.checksum.utils

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.{okXml, post, urlEqualTo}
import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.{Parameters, ResponseDefinitionTransformer}
import com.github.tomakehurst.wiremock.http.{Request, ResponseDefinition}
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.stubbing.{ServeEvent, StubMapping}
import io.circe.generic.auto._
import io.circe.parser.decode

import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.Base64
import scala.io.Source.fromResource
import scala.jdk.CollectionConverters._
import scala.xml.Elem

object TestUtils {

  def receiptHandle(body: String): String = Base64.getEncoder.encodeToString(body.getBytes("UTF-8"))

  def urlEncodeFile(location: String): String = URLEncoder.encode(
    fromResource(s"json/$location.json").filterNot(_.isWhitespace).mkString, Charset.defaultCharset()
  )

  def createEvent(locations: String*): SQSEvent = {
    val event = new SQSEvent()

    val records = locations.map(location => {
      val record = new SQSMessage()
      val body = fromResource(s"json/$location.json").mkString
      record.setBody(body)
      record.setReceiptHandle(receiptHandle(body))
      record
    })

    event.setRecords(records.asJava)
    event
  }

  val port = 8002

  def url(queueName: String) = s"http://localhost:8002/1/test_${queueName}_queue"

  val outputQueue: String = URLEncoder.encode(url("output"), Charset.defaultCharset())
  val inputQueue: String = URLEncoder.encode(url("input"), Charset.defaultCharset())

  def sendMessageXml(md5: String): Elem =
    <SendMessageResponse>
      <SendMessageResult>
        <MD5OfMessageBody>{md5}</MD5OfMessageBody>
        <MD5OfMessageAttributes>3ae8f24a165a8cedc005670c81a27295</MD5OfMessageAttributes>
        <MessageId>5fea7756-0ea4-451a-a703-a558b933e274</MessageId>
      </SendMessageResult>
      <ResponseMetadata>
        <RequestId>27daac76-34dd-47df-bd01-1f6e873584a0</RequestId>
      </ResponseMetadata>
    </SendMessageResponse>

  val deleteMessageXml: Elem =
    <DeleteMessageResponse>
      <ResponseMetadata>
        <RequestId>b5293cb5-d306-4a17-9048-b263635abe42</RequestId>
      </ResponseMetadata>
    </DeleteMessageResponse>

  val wiremockSqsEndpoint = new WireMockServer(new WireMockConfiguration().port(port))

  val wiremockKmsEndpoint = new WireMockServer(new WireMockConfiguration().port(9003).extensions(new ResponseDefinitionTransformer {
    override def transform(request: Request, responseDefinition: ResponseDefinition, files: FileSource, parameters: Parameters): ResponseDefinition = {
      case class KMSRequest(CiphertextBlob: String)
      decode[KMSRequest](request.getBodyAsString) match {
        case Left(err) => throw err
        case Right(req) =>
          val charset = Charset.defaultCharset()
          val plainText = charset.newDecoder.decode(ByteBuffer.wrap(req.CiphertextBlob.getBytes(charset))).toString
          ResponseDefinitionBuilder
            .like(responseDefinition)
            .withBody(s"""{"Plaintext": "$plainText"}""")
            .build()
      }
    }
    override def getName: String = ""
  }))

  def stubSendMessage(md5Sum: String = "7fa99856b45866889512524430db4208"): StubMapping =
    wiremockSqsEndpoint.stubFor(post(urlEqualTo("/"))
      .withRequestBody(new ContainsPattern("SendMessage"))
      .willReturn(okXml(sendMessageXml(md5Sum).toString())))

  def stubDeleteMessage(): StubMapping =
    wiremockSqsEndpoint.stubFor(post(urlEqualTo("/"))
      .withRequestBody(new ContainsPattern("DeleteMessage"))
      .willReturn(okXml(deleteMessageXml.toString())))


  def getEventsForFilters(filterParams: (String, String)*): List[ServeEvent] =
    wiremockSqsEndpoint.getAllServeEvents.asScala.toList.filter(event => {
      val bodyParams: Map[String, String] = event.getRequest.getBodyAsString.split("&").map(param => {
        val params = param.split("=")
        (params(0), params(1))
      }).toMap

      filterParams.map(filterParam =>
        bodyParams.get(filterParam._1).contains(filterParam._2)
      ).forall(p => p)
    })

  def outputSentMessageCount: Int = getEventsForFilters(("QueueUrl", outputQueue), ("Action", "SendMessage")).size
  def inputDeleteMessageCount: Int = getEventsForFilters(("QueueUrl", outputQueue), ("Action", "SendMessage")).size
}
