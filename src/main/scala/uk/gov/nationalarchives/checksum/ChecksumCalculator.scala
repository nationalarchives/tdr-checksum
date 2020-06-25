package uk.gov.nationalarchives.checksum

import java.net.URI
import java.util.UUID

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNS
import com.amazonaws.services.lambda.runtime.events.{S3Event, SQSEvent}
import com.typesafe.config.ConfigFactory
import graphql.codegen.types.AddFileMetadataInput
import io.circe
import io.circe.parser.decode
import io.circe.syntax._
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.SqsClient
import uk.gov.nationalarchives.checksum.AWSDecoders._
import java.net.URLDecoder

import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.Try

class ChecksumCalculator {

  case class EventWithReceiptHandle(event: S3Event, receiptHandle: String)

  def update(event: SQSEvent, context: Context): List[String] = {
    val eventsOrError: List[Either[circe.Error, EventWithReceiptHandle]] = event.getRecords.asScala.map(record => {
      for {
        snsDecoded <- decode[SNS](record.getBody)
        s3 <- decode[S3Event](snsDecoded.getMessage)
      } yield EventWithReceiptHandle(s3, record.getReceiptHandle)
    }).toList

    val (decodingFailed: List[Throwable], decodingSucceeded: List[EventWithReceiptHandle]) = eventsOrError.partitionMap(identity)

    val configFactory = ConfigFactory.load

    val httpClient = ApacheHttpClient.builder.build
    val sqsClient: SqsClient = SqsClient.builder()
      .region(Region.EU_WEST_2)
      .endpointOverride(URI.create(configFactory.getString("sqs.endpoint")))
      .httpClient(httpClient)
      .build()

    val sqsUtils: SQSUtils = SQSUtils(sqsClient)

    val values: List[Either[Throwable, String]] = decodingSucceeded.flatMap(eventWithReceiptHandle => eventWithReceiptHandle.event.getRecords.asScala.toList.map(record => {
      val s3 = record.getS3
      val s3Client: S3Client = S3Client.builder
        .region(Region.EU_WEST_2)
        .endpointOverride(URI.create(configFactory.getString("s3.endpoint")))
        .httpClient(httpClient)
        .build()

      val uploadBucketClient = new UploadBucketClient(s3Client, s3.getBucket.getName, URLDecoder.decode(s3.getObject.getKey, "utf-8"))
      val checksumGenerator = Try(ChecksumGenerator().generate(uploadBucketClient, record.getS3.getObject.getSizeAsLong)).toEither

      checksumGenerator.map(checksum => {
        print(checksum)
        val keyToArray: Array[String] = s3.getObject.getKey.split("/")
        val fileId = UUID.fromString(keyToArray.last)

        val messageBody = AddFileMetadataInput("SHA256ServerSideChecksum", fileId, checksum).asJson.noSpaces
        sqsUtils.send(configFactory.getString("sqs.queue.output"), messageBody)

        print(s"checksum calculated for file id $fileId in message with ${eventWithReceiptHandle.receiptHandle}")
        eventWithReceiptHandle.receiptHandle
      })
    }))

    val (checksumCalculationFailed: List[Throwable], checksumCalculationSucceeded: List[String]) = values.partitionMap(identity)

    val allErrors: List[Throwable] = checksumCalculationFailed ++ decodingFailed

    if (allErrors.nonEmpty) {
      checksumCalculationSucceeded.foreach(handle => sqsUtils.delete(configFactory.getString("sqs.queue.input"), handle))
      throw new RuntimeException(allErrors.map(_.getMessage).mkString(", "))
    } else {

      checksumCalculationSucceeded
    }
  }
}
