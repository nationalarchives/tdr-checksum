package uk.gov.nationalarchives.checksum

import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.jdk.CollectionConverters._

class ChecksumGeneratorTest extends AnyFlatSpec with MockitoSugar with TableDrivenPropertyChecks {

  val fileSizes =
    Table(
      "fileSizeInMb",
      1,
      10,
      200
    )

  forAll(fileSizes) { fileSizeInMb => {
    "The generate method" should s"call the bucket client with the correct start and end values for file size $fileSizeInMb" in {
      val checksumGenerator = ChecksumGenerator()
      val uploadBucketClient = mock[UploadBucketClient]
      val startCaptor: ArgumentCaptor[Long] = ArgumentCaptor.forClass(classOf[Long])
      val endCaptor: ArgumentCaptor[Long] = ArgumentCaptor.forClass(classOf[Long])
      val response = "teststring".getBytes
      when(uploadBucketClient.getBytesFromS3Object(startCaptor.capture(), endCaptor.capture())).thenReturn(response)
      checksumGenerator.generate(uploadBucketClient, fileSizeInMb * 1024 * 1024)
      val startValues: List[Long] = startCaptor.getAllValues.asScala.toList
      val endValues = endCaptor.getAllValues.asScala.toList

      for (idx <- 0 until fileSizeInMb) {
        startValues(idx) should equal(1024 * 1024 * idx)
        endValues(idx) should equal((1024 * 1024 * (idx + 1)) - 1)
      }
    }
  }
  }
}
