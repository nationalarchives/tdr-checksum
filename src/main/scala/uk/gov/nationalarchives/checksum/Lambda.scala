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
import software.amazon.awssdk.services.sqs.model.{DeleteMessageResponse, SendMessageResponse}
import uk.gov.nationalarchives.aws.utils.Clients.{kms, sqs}
import uk.gov.nationalarchives.aws.utils.{KMSUtils, SQSUtils}
import uk.gov.nationalarchives.checksum.ChecksumGenerator.ChecksumFile
import graphql.codegen.types.AddFileMetadataInput
import com.typesafe.scalalogging.Logger

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

  val logger: Logger = Logger[Lambda]

  case class ChecksumFileWithReceiptHandle(checksumFile: ChecksumFile, receiptHandle: String)

  def decodeBody(record: SQSMessage): IO[ChecksumFileWithReceiptHandle] = {
    IO.fromEither(decode[ChecksumFile](record.getBody)
      .map(checksumFile => ChecksumFileWithReceiptHandle(checksumFile, record.getReceiptHandle)))
  }

  def process(event: SQSEvent, context: Context): List[String] = {
    val results = event.getRecords.asScala.toList
      .map(sqsMessage => {
        for {
          body <- decodeBody(sqsMessage)
          checksum <- ChecksumGenerator(lambdaConfig).generate(body.checksumFile)
          _ <- IO(sendMessage(AddFileMetadataInput("SHA256ServerSideChecksum", body.checksumFile.fileId, checksum).asJson.noSpaces))
        } yield body.receiptHandle
      })

    val (failed, succeeded) = results.map(_.attempt).sequence.unsafeRunSync().partitionMap(identity)
    if (failed.nonEmpty) {
      failed.foreach(e => logger.error(e.getMessage, e))
      succeeded.map(deleteMessage)
      throw new RuntimeException(failed.mkString("\n"))
    } else {
      succeeded
    }
  }
}
