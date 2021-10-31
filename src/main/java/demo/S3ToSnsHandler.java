package demo;

import static com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class S3ToSnsHandler implements RequestHandler<S3Event, Void> {

    private static final Logger logger = LoggerFactory.getLogger(S3ToSnsHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final boolean serializeToJson = Boolean.parseBoolean(System.getenv("SerializeToJSON"));

    @Override
    public Void handleRequest(S3Event event, Context context) {
        logger.info("S3 event: {}", toString(event));
        logger.info("context: {}", toString(context));

        List<String> messages = new ArrayList<>();
        for (S3EventNotificationRecord record : event.getRecords()) {
            logger.info("S3 message: {}", toString(record));

            String eventName = record.getEventName();
            logger.info("S3 event name: {}", eventName);

            String bucketName = record.getS3().getBucket().getName();
            logger.info("S3 bucket: {}", bucketName);
            String objectKey = record.getS3().getObject().getKey();
            logger.info("S3 key: {}", objectKey);

            if ("ObjectCreated:Put".equals(eventName)) {
                messages.add(String.format("Object %s is created in bucket %s", objectKey, bucketName));
            }
            if ("ObjectRemoved:Delete".equals(eventName)) {
                messages.add(String.format("Object %s is removed from bucket %s", objectKey, bucketName));
            }
        }

        String body = String.join("\n", messages);
        logger.info("SNS message body: {}", toString(body));

        String region = System.getenv("Region");
        logger.info("region: {}", region);
        String TopicARN = System.getenv("TopicARN");
        logger.info("topic ARN: {}", TopicARN);

        PublishRequest publishRequest = new PublishRequest(TopicARN, body);
        logger.info("SNS publish request: {}", toString(publishRequest));

        PublishResult publishResult = getAmazonSNS(region).publish(publishRequest);
        logger.info("SNS publish result: {}", toString(publishResult));

        return null;
    }

    AmazonSNS getAmazonSNS(String region) {
        return AmazonSNSClient.builder()
                .withRegion(Regions.fromName(region))
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();
    }

    private String toString(Object obj) {
        return serializeToJson ? gson.toJson(obj) : "" + obj;
    }
}
