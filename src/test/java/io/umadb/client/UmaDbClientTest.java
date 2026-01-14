package io.umadb.client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

public class UmaDbClientTest {

    UmaDbClient client;

    @BeforeEach
    void setUp() {
        this.client = new UmaDbClient("localhost", 50051);
    }

    @Test
    void testSimpleAppend() {
        var data = "Hello123".getBytes(UTF_8);
        var type = "test-type";

        var eventsToAppend = List.of(
                new Event(
                    type,
                    List.of(),
                    data,
                    null
                )
        );

        var appendRequest = new AppendRequest(
                eventsToAppend,
                null
        );

        var response = client.handle(appendRequest);


        assertNotNull(response);
        assertTrue(response.position() > 0);

        assertEquals(response.position(), client.getHeadPosition());

    }

    @Test
    void testStreaming() {
        var readRequest = new ReadRequest(
                null,
                0L,
                null,
                null,
                null,
                null
        );
        var iterator = client.handle(readRequest);

        iterator.forEachRemaining(response -> {
            System.out.println(response.events());
        });
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        client.shutdown();
    }

}
