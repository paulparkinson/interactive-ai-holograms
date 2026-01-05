package oracleai;

import com.oracle.bmc.retrier.RetryConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class AIApplication {

    public static final String COMPARTMENT_ID = oracleai.aiholo.Configuration.getCompartmentId();
    public static final String OBJECTSTORAGE_NAMESPACE = oracleai.aiholo.Configuration.getObjectStorageNamespace();
    public static final String OBJECTSTORAGE_BUCKETNAME = oracleai.aiholo.Configuration.getObjectStorageBucketName();
    public static final String ORDS_ENDPOINT_URL = oracleai.aiholo.Configuration.getOrdsEndpointUrl();
    public static final String ORDS_OMLOPSENDPOINT_URL = oracleai.aiholo.Configuration.getOrdsOmlOpsEndpointUrl();
    public static final String OCI_VISION_SERVICE_ENDPOINT = oracleai.aiholo.Configuration.getOciVisionServiceEndpoint();
    public static final String OCICONFIG_FILE = oracleai.aiholo.Configuration.getOciConfigFile();
    public static final String OCICONFIG_PROFILE = oracleai.aiholo.Configuration.getOciConfigProfile();
    public static final String DIGITAL_DOUBLES_IMAGES_ENDPOINT = oracleai.aiholo.Configuration.getDigitalDoublesImagesEndpoint();
    public static final String THREEDEY = oracleai.aiholo.Configuration.getThreeDKey();

    static {
        System.out.println("AIApplication.static initializer COMPARTMENT_ID:" + COMPARTMENT_ID);
        System.out.println("AIApplication.static initializer OBJECTSTORAGE_NAMESPACE:" + OBJECTSTORAGE_NAMESPACE);
        System.out.println("AIApplication.static initializer OBJECTSTORAGE_BUCKETNAME:" + OBJECTSTORAGE_BUCKETNAME);
        System.out.println("AIApplication.static initializer ORDS_ENDPOINT_URL:" + ORDS_ENDPOINT_URL);
        System.out.println("AIApplication.static initializer OCI_VISION_SERVICE_ENDPOINT:" + OCI_VISION_SERVICE_ENDPOINT);
    }

    public static void main(String[] args) {
//        RetryConfiguration retryConfiguration = RetryConfiguration.builder()
//                .terminationStrategy(RetryUtils.createExponentialBackoffStrategy(500, 5)) // Configure limits
//                .build();
        SpringApplication.run(AIApplication.class, args);
    }

}
