## Checksum Calculator

This is the code for the lambda which calculates the checksum for a single file. 

The function receives `{"consignmentId": "xxxx-xxxx-xxxx", "userId": "xxxx-xxxx-xxxx", "fileId": "xxxx-xxxx-xxxx"}` as input. 
It then downloads the file from S3 and calculates the checksum and returns `{"fileId": "xxxx-xxxx-xxxx", "checksum": "calculatedchecksum"}` 

### Running locally
You will need credentials for the AWS environment you are running this for set either as environment variables in the debug configuration or as a profile in `~/.aws/credentials`

In the [LambdaRunner](src/main/scala/uk/gov/nationalarchives/checksum/LambdaRunner.scala) class, replace `consignmentId`, `userId` and `fileId` with the values you want to test. 

The default environment variables are set for a chunk size of 100Mb and the integration S3 bucket but these can be changed by overriding `CHUNK_SIZE_IN_MB` and `S3_BUCKET`

Run the `LambdaRunner` class.
