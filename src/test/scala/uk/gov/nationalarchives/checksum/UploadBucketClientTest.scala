package uk.gov.nationalarchives.checksum

import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.mockito.ArgumentMatchers._
import org.scalatest.flatspec.AnyFlatSpec
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableDrivenPropertyChecks

class UploadBucketClientTest extends AnyFlatSpec with MockitoSugar with TableDrivenPropertyChecks {

  val startAndEndPositions =
    Table(
      ("start", "end"),
      (0, 10),
      (30, 100),
      (5000, 1000)
    )

  val bucketsAndKeys =
    Table(
      ("bucket", "key"),
      ("testBucket1", "testKey1"),
      ("testBucket2", "testKey2"),
      ("testBucket3", "testKey3")
    )

  "The getBytes method" should s"return the correct s3 object" in {
    val s3Client: S3Client = mock[S3Client]

    val getObjectResponse = GetObjectResponse.builder().build()
    val response: ResponseBytes[GetObjectResponse] = ResponseBytes.fromByteArray(getObjectResponse, Array[Byte]())
    when(s3Client.getObject(any[GetObjectRequest], any[ResponseTransformer[GetObjectResponse, ResponseBytes[GetObjectResponse]]])).thenReturn(response)
    val uploadBucketClient = UploadBucketClient(s3Client, "testBucket", "testKey")
    val result = uploadBucketClient.getBytesFromS3Object(0, 10)

    result should equal(response.asByteArray)
  }

  forAll(startAndEndPositions) { (start, end) => {
    "The getBytes method" should s"return the correct s3 object for start $start and end $end" in {
      val s3Client: S3Client = mock[S3Client]
      val bucketName = "testBucket"
      val key = "testKey"

      val objectRequestCaptor: ArgumentCaptor[GetObjectRequest] = ArgumentCaptor.forClass(classOf[GetObjectRequest])
      val getObjectResponse = GetObjectResponse.builder().build()
      val response: ResponseBytes[GetObjectResponse] = ResponseBytes.fromByteArray(getObjectResponse, Array[Byte]())
      when(s3Client.getObject(objectRequestCaptor.capture(), any[ResponseTransformer[GetObjectResponse, ResponseBytes[GetObjectResponse]]])).thenReturn(response)
      val uploadBucketClient = UploadBucketClient(s3Client, bucketName, key)
      uploadBucketClient.getBytesFromS3Object(start, end)

      val objectRequest = objectRequestCaptor.getValue
      objectRequest.range should equal(s"bytes=$start-$end")
    }
  }
  }

  forAll(bucketsAndKeys) { (bucket, key) => {
    "The getBytes method" should s"return the correct s3 object for bucket $bucket and key $key" in {
      val s3Client: S3Client = mock[S3Client]
      val bucketName = "testBucket"
      val key = "testKey"

      val objectRequestCaptor: ArgumentCaptor[GetObjectRequest] = ArgumentCaptor.forClass(classOf[GetObjectRequest])
      val getObjectResponse = GetObjectResponse.builder().build()
      val response: ResponseBytes[GetObjectResponse] = ResponseBytes.fromByteArray(getObjectResponse, Array[Byte]())
      when(s3Client.getObject(objectRequestCaptor.capture(), any[ResponseTransformer[GetObjectResponse, ResponseBytes[GetObjectResponse]]])).thenReturn(response)
      val uploadBucketClient = UploadBucketClient(s3Client, bucketName, key)
      uploadBucketClient.getBytesFromS3Object(0, 10)

      val objectRequest = objectRequestCaptor.getValue
      objectRequest.bucket should equal(bucketName)
      objectRequest.key should equal(key)
    }
  }
  }
}
