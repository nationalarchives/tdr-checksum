package uk.gov.nationalarchives.checksum

import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse}

class UploadBucketClient(s3Client: S3Client, bucketName: String, key: String) {

  def getBytesFromS3Object(start: Long, end: Long): Array[Byte] = {
    val objectRequest: GetObjectRequest = GetObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .range(s"bytes=$start-$end")
      .build()
    val byteTransformer: ResponseTransformer[GetObjectResponse, ResponseBytes[GetObjectResponse]] = ResponseTransformer.toBytes()
    s3Client.getObject(objectRequest, byteTransformer).asByteArray()
  }
}

object UploadBucketClient {
  def apply(s3Client: S3Client, bucketName: String, key: String): UploadBucketClient = new UploadBucketClient(s3Client, bucketName, key)
}
