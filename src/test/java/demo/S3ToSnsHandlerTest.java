package demo;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import org.easymock.Capture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class S3ToSnsHandlerTest {

    @ParameterizedTest
    @MethodSource("methodSource")
    public void handleRequestTest(String fileName, String message) throws Exception {
        URL fileURL = getClass().getClassLoader().getResource(fileName);
        String json = Files.readString(Path.of(fileURL.toURI()), StandardCharsets.UTF_8);
        S3Event event = new S3Event(S3Event.parseJson(json).getRecords());

        Map<String, String> environment = new HashMap<>();
        environment.put("Region", "eu-north-1");
        environment.put("TopicARN", "arn:aws:sns:::target-topic");
        setEnvironment(environment);

        Context context = createMock(Context.class);
        AmazonSNS amazonSNS = createMock(AmazonSNS.class);

        S3ToSnsHandler handler = new S3ToSnsHandler() {
            @Override
            AmazonSNS getAmazonSNS(String region) {
                assertEquals("eu-north-1", region);
                return amazonSNS;
            }
        };

        Capture<PublishRequest> publishRequestCapture = newCapture();
        PublishResult publishResult = createMock(PublishResult.class);

        expect(amazonSNS.publish(capture(publishRequestCapture))).andReturn(publishResult).once();

        replay(context, amazonSNS, publishResult);
        handler.handleRequest(event, context);
        verify(context, amazonSNS, publishResult);

        PublishRequest publishRequest = publishRequestCapture.getValue();
        assertEquals(message, publishRequest.getMessage());
        assertEquals("arn:aws:sns:::target-topic", publishRequest.getTopicArn());
    }

    private static Stream<Arguments> methodSource() {
        return Stream.of(
                Arguments.of("ObjectCreatedPut.json", "Object test/key is created in bucket source-bucket"),
                Arguments.of("ObjectRemovedDelete.json", "Object test/key is removed from bucket source-bucket")
        );
    }

    private void setEnvironment(Map<String, String> environment) throws Exception {
        try {
            Class<?> clazz = Class.forName("java.lang.ProcessEnvironment");
            updatePrivateMap(clazz, null, "theEnvironment", environment);
            updatePrivateMap(clazz, null, "theCaseInsensitiveEnvironment", environment);
        } catch (NoSuchFieldException e) {
            Class<?>[] classes = Collections.class.getDeclaredClasses();
            for (Class<?> clazz : classes) {
                if ("java.util.Collections$UnmodifiableMap".equals(clazz.getName())) {
                    updatePrivateMap(clazz, System.getenv(), "m", environment);
                }
            }
        }
    }

    private void updatePrivateMap(Class<?> clazz, Object obj, String fieldName, Map<String, String> environment) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        Map<String, String> map = (Map<String, String>) field.get(obj);
        map.putAll(environment);
    }
}
