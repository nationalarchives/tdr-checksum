package uk.gov.nationalarchives.checksum

import java.security.MessageDigest

import com.typesafe.config.ConfigFactory

class ChecksumGenerator {

  def generate(uploadBucketClient: UploadBucketClient, fileSizeInBytes: Long): String = {
    val configFactory = ConfigFactory.load
    val chunkSizeInMB = configFactory.getLong("chunk.size")

    val chunkSizeInBytes: Long = chunkSizeInMB * 1024 * 1024
    val messageDigester: MessageDigest = MessageDigest.getInstance("SHA-256")

    for(startByte <- 0L until fileSizeInBytes by chunkSizeInBytes) {
      val bytesFromFile: Array[Byte] = uploadBucketClient.getBytesFromS3Object(startByte,  startByte + chunkSizeInBytes-1)
      messageDigester.update(bytesFromFile)
    }
    val checkSum: Array[Byte] = messageDigester.digest
    checkSum.map(byte => f"$byte%02x").mkString
  }
}

object ChecksumGenerator {
  def apply(): ChecksumGenerator = new ChecksumGenerator()
}
