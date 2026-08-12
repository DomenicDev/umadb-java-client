package io.umadb.client;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TLS and API key tests for the asynchronous client, mirroring {@link UmaDbSecureClientTest}.
 */
@Testcontainers
class UmaDbAsyncSecureClientTest {

    private static final int TIMEOUT_SECONDS = 5;

    private static final String TEST_API_KEY = "test-123";
    private static final String PATH_TLS_CERT = "test-certificate/server.pem";
    private static final String PATH_TLS_KEY = "test-certificate/server.key";

    private static final String CLASS_PATH_TLS_CERT = getTlsClassPath();

    @Container
    private static final UmaDbContainer SECURED_UMA_DB_CONTAINER = new UmaDbContainer()
            .withTlsCert(PATH_TLS_CERT)
            .withTlsKey(PATH_TLS_KEY)
            .withApiKey(TEST_API_KEY);

    @Test
    void testSecureConnectionWithProperlyConfiguredClient() throws Exception {
        var client = UmaDbClient.builder()
                .withHost(SECURED_UMA_DB_CONTAINER.getHost())
                .withPort(SECURED_UMA_DB_CONTAINER.getExposedGrpcPort())
                .withCertificateAuthority(CLASS_PATH_TLS_CERT)
                .withApiKey(TEST_API_KEY)
                .buildAsync();

        client.connect();

        var response = client.handle(getSampleAppendRequest()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(response.position() > 0);

        client.shutdown();
    }

    @Test
    void testConnectWithAnInsecureClient() {
        var client = UmaDbClient.builder()
                .withHost(SECURED_UMA_DB_CONTAINER.getHost())
                .withPort(SECURED_UMA_DB_CONTAINER.getExposedGrpcPort())
                .buildAsync();

        client.connect();

        var thrown = assertThrows(
                ExecutionException.class,
                () -> client.handle(getSampleAppendRequest()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        );
        assertInstanceOf(UmaDbException.class, thrown.getCause());

        client.shutdown();
    }

    @Test
    void testConnectWithSecureClientButNoApiKey() {
        var client = UmaDbClient.builder()
                .withHost(SECURED_UMA_DB_CONTAINER.getHost())
                .withPort(SECURED_UMA_DB_CONTAINER.getExposedGrpcPort())
                .withCertificateAuthority(CLASS_PATH_TLS_CERT)
                .buildAsync();

        client.connect();

        var thrown = assertThrows(
                ExecutionException.class,
                () -> client.handle(getSampleAppendRequest()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        );
        assertInstanceOf(UmaDbException.AuthenticationException.class, thrown.getCause());

        client.shutdown();
    }

    @Test
    void testApiKeyWithoutTlsIsRejectedAtBuildTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new io.umadb.client.grpc.UmaDbAsyncClientImpl(
                        "localhost", 50051, false, null, "some-key", null
                )
        );
    }

    private static AppendRequest getSampleAppendRequest() {
        return AppendRequest.of(
                List.of(
                        Event.of(
                                "type",
                                List.of("tag1"),
                                "data".getBytes(UTF_8)
                        )
                )
        );
    }

    private static String getTlsClassPath() {
        return ClassLoader.getSystemResource(PATH_TLS_CERT).getPath();
    }
}
