package uk.gov.nationalarchives.checksum

import cats.effect.IO
import cats.implicits._
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode
import software.amazon.awssdk.services.sqs.model.{ChangeMessageVisibilityResponse, DeleteMessageResponse, SendMessageResponse}
import uk.gov.nationalarchives.aws.utils.Clients.{kms, sqs}
import uk.gov.nationalarchives.aws.utils.{KMSUtils, SQSUtils}
import uk.gov.nationalarchives.checksum.ChecksumGenerator.ChecksumFile
import graphql.codegen.types.AddFileMetadataInput
import com.typesafe.scalalogging.Logger
import net.logstash.logback.argument.StructuredArguments.value

import java.time.Instant
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class Lambda {

  val configFactory: Config = ConfigFactory.load
  val sqsUtils: SQSUtils = SQSUtils(sqs)
  val kmsUtils: KMSUtils = KMSUtils(kms(configFactory.getString("kms.endpoint")), Map("LambdaFunctionName" -> configFactory.getString("function.name")))
  val lambdaConfig: Map[String, String] = kmsUtils.decryptValuesFromConfig(
    List("sqs.queue.input", "sqs.queue.output", "efs.root.location", "chunk.size")
  )
  val deleteMessage: String => DeleteMessageResponse = sqsUtils.delete(lambdaConfig("sqs.queue.input"), _)
  val sendMessage: String => SendMessageResponse = sqsUtils.send(lambdaConfig("sqs.queue.output"), _)
  val resetMessageVisibility: String => ChangeMessageVisibilityResponse = receiptHandle => sqsUtils.makeMessageVisible(lambdaConfig("sqs.queue.input"), receiptHandle)

  val logger: Logger = Logger[Lambda]

  case class ChecksumFileWithReceiptHandle(checksumFile: ChecksumFile, receiptHandle: String)

  def decodeBody(record: SQSMessage): IO[ChecksumFileWithReceiptHandle] = {
    IO.fromEither(decode[ChecksumFile](record.getBody)
      .map(checksumFile => ChecksumFileWithReceiptHandle(checksumFile, record.getReceiptHandle)))
  }

  def process(event: SQSEvent, context: Context): List[String] = {
    val startTime = Instant.now
    val results = event.getRecords.asScala.toList
      .map(sqsMessage => {
        val decodedBody = decodeBody(sqsMessage)
        val receiptHandleOrError = for {
          body <- decodedBody
          checksum <- ChecksumGenerator(lambdaConfig).generate(body.checksumFile)
          _ <- IO(sendMessage(AddFileMetadataInput("SHA256ServerSideChecksum", body.checksumFile.fileId, checksum).asJson.noSpaces))
        } yield body

        receiptHandleOrError.handleErrorWith(err => decodedBody.flatMap(body => {
          resetMessageVisibility(body.receiptHandle)
          IO.raiseError(err)
        }))
      })

    val (failed, succeeded) = results.map(_.attempt).sequence.unsafeRunSync().partitionMap(identity)
    if (failed.nonEmpty) {
      failed.foreach(e => logger.error(e.getMessage, e))
      succeeded.map(s => deleteMessage(s.receiptHandle))
      throw new RuntimeException(failed.mkString("\n"))
    } else {
      succeeded.map(success => {
        val timeTaken = java.time.Duration.between(startTime, Instant.now).toMillis.toDouble / 1000
        logger.info(
          s"Lambda complete in {} seconds for file ID '{}' and consignment ID '{}'",
          value("timeTaken", timeTaken),
          value("fileId", success.checksumFile.fileId),
          value("consignmentId", success.checksumFile.consignmentId)
        )
        success.receiptHandle
      })

    }
  }
}
