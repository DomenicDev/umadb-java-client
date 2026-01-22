package io.umadb.client;

import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class UmaDbSecureClientTest {

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
    void testSecureConnectionWithProperlyConfiguredClient() {
        var client = UmaDbClient.builder()
                .withHost(SECURED_UMA_DB_CONTAINER.getHost())
                .withPort(SECURED_UMA_DB_CONTAINER.getExposedGrpcPort())
                .withTls(CLASS_PATH_TLS_CERT)
                .withApiKey(TEST_API_KEY)
                .build();

        client.connect();

        var response = client.handle(getSampleAppendRequest());
        assertTrue(response.position() > 0);

        client.shutdown();
    }

    @Test
    void testConnectWithAnInsecureClient() {
        assertThrows(
                UmaDbException.class,
                () -> {
                    var client = UmaDbClient.builder()
                            .withHost(SECURED_UMA_DB_CONTAINER.getHost())
                            .withPort(SECURED_UMA_DB_CONTAINER.getExposedGrpcPort())
                            .build();

                    client.connect();

                    client.handle(getSampleAppendRequest());
                });
    }

    @Test
    void testConnectWithSecureClientButNoApiKey() {
        assertThrows(
                UmaDbException.AuthenticationException.class,
                () -> {
                    var client = UmaDbClient.builder()
                            .withHost(SECURED_UMA_DB_CONTAINER.getHost())
                            .withPort(SECURED_UMA_DB_CONTAINER.getExposedGrpcPort())
                            .withTls(CLASS_PATH_TLS_CERT)
                            .build();

                    client.connect();

                    client.handle(getSampleAppendRequest());
                });
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
