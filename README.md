## Checksum Calculator

This is the code for the lambda which calculates the checksum for a single file. The function receives an sqs event containing an s3 update mesasge, streams the file in memory and calculates the checksum which it then sends to the output queue.

### Running locally
There is no way currently of running this in a fully local environment so to run this locally, you have to provide dummy json in an SQS event. An example of the code is here:

```scala
object Test extends App {
  import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
  import com.amazonaws.services.lambda.runtime.events.SQSEvent
  import scala.jdk.CollectionConverters._
  
  val calc = new ChecksumCalculator()
  val json =
    """
      |{
      |  "Type": "Notification",
      |  "MessageId": "d138ef90-e606-513b-8e8d-b8c0e7d4183e",
      |  "TopicArn": "",
      |  "Subject": "",
      |  "Message": "{\"Records\":[{\"eventVersion\":\"2.1\",\"eventSource\":\"aws:s3\",\"awsRegion\":\"eu-west-2\",\"eventTime\":\"2020-06-02T07:28:12.755Z\",\"eventName\":\"ObjectCreated:Put\",\"userIdentity\":{\"principalId\":\"\"},\"requestParameters\":{\"sourceIPAddress\":\"\"},\"responseElements\":{\"x-amz-request-id\":\"\",\"x-amz-id-2\":\"\"},\"s3\":{\"s3SchemaVersion\":\"1.0\",\"configurationId\":\"\",\"bucket\":{\"name\":\"test-bucket\",\"ownerIdentity\":{\"principalId\":\"\"},\"arn\":\"arn:aws:s3:::test-bucket\"},\"object\":{\"key\":\"consignmentId/ec6a4bce-65b3-4189-8450-e912c4a32b16\",\"size\":0,\"eTag\":\"\",\"versionId\":\"\",\"sequencer\":\"\"}}}]}",
      |  "Timestamp": "2020-06-02T07:28:18.365Z",
      |  "SignatureVersion": "1",
      |  "Signature": "",
      |  "SigningCertURL": "",
      |  "UnsubscribeURL": ""
      |}
      |""".stripMargin
  val record = new SQSMessage()
  record.setBody(json)
  val event = new SQSEvent()
  event.setRecords(List(record).asJava)
  calc.update(event, null)
}
```

Then add the account number into the application.conf file for the output queue. You can add it for the input queue but as the input queue isn't being used, you don't have to.

Run the test code. If it's successful, it should add the checksum message to the output queue.

### Adding new environment variables to the tests
The environment variables in the deployed lambda are encrypted using KMS and then base64 encoded. These are then decoded in the lambda. Because of this, any variables in `src/test/resources/application.conf` which come from environment variables in `src/main/resources/application.conf` need to be stored base64 encoded. There are comments next to each variable to say what the base64 string decodes to. If you want to add a new variable you can run `echo -n "value of variable" | base64 -w 0` and paste the output into the test application.conf