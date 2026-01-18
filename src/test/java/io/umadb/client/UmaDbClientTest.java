package io.umadb.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class UmaDbClientTest {

    @Container
    private static final UmaDbContainer UMA_DB_CONTAINER = new UmaDbContainer();

    UmaDbClient client;

    @BeforeEach
    void setUp() {
        this.client = new UmaDbClient(
                UMA_DB_CONTAINER.getHost(),
                UMA_DB_CONTAINER.getExposedGrpcPort()
        );
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
    void testSimpleRead() {
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

    @Test
    void testConditionalAppendIntegrityError() {
        // Initial setup: Define the consistency boundary
        var consistencyBoundary = new QueryItem(
                List.of("example"), // event types
                List.of("tag1", "tag2") // tags
        );
        var query = new Query(List.of(consistencyBoundary));

        var lastPosition = client.getHeadPosition();

        // First event to append (successful)
        var event = new Event(
                "example", // event type
                List.of("tag1", "tag2"), // tags
                "Hello, World!".getBytes(UTF_8),
                null
        );

        var appendRequest1 = new AppendRequest(
                List.of(event),
                new AppendCondition(
                        query,
                        lastPosition
                )
        );

        // Append first event, expect success
        var response1 = client.handle(appendRequest1);
        long lastKnownPosition = response1.position();
        System.out.println("First event appended at position: " + lastKnownPosition);

        // Second event, same as first, should fail due to integrity error
        var conflictingEvent = new Event(
                "example",
                List.of("tag1", "tag2"),
                "Hello, World!".getBytes(UTF_8),
                null
        );
        var appendRequest2 = new AppendRequest(
                List.of(conflictingEvent),
                new AppendCondition(
                        query, // same consistency boundary
                        lastPosition
                )
        );

        assertThrows(
                UmaDbException.IntegrityException.class,
                () -> client.handle(appendRequest2)
        );

    }

    @Test
    void testIdempotentAppend() {
        // Define the consistency boundary (same as in previous test)

        var consistencyBoundary = new QueryItem(
                List.of("example"), // event types
                List.of("tag1", "tag2") // tags
        );
        var query = new Query(List.of(consistencyBoundary));

        // First event to append
        var event = new Event(
                "example", // event type
                List.of("tag1", "tag2"), // tags
                "Hello, World!".getBytes(UTF_8),
                UUID.randomUUID()
        );

        var lastPosition = client.getHeadPosition();
        var appendRequest1 = new AppendRequest(
                List.of(event),
                new AppendCondition(
                        query, // same boundary
                        lastPosition
                )
        );

        // Append first event, expect success
        var response1 = client.handle(appendRequest1);
        long commitPosition1 = response1.position();
        System.out.println("First event appended at position: " + commitPosition1);

        // Retry with the same event
        var appendRequest2 = new AppendRequest(
                List.of(event),
                new AppendCondition(
                        query, // same boundary
                        lastPosition
                )
        );
        var response2 = client.handle(appendRequest2);
        long commitPosition2 = response2.position();

        // The commit position should be the same, indicating idempotent retry
        assertEquals(commitPosition1, commitPosition2);
        System.out.println("Idempotent retry returned position: " + commitPosition2);
    }

    @Test
    void testGetHeadPosition() {
        long headPosition = client.getHeadPosition();
        System.out.println("Current head position: " + headPosition);

        // Append a new event
        var event = new Event(
                "example", // event type
                List.of("tag1", "tag2"), // tags
                "New Event".getBytes(UTF_8),
                UUID.randomUUID()
        );

        var appendRequest = new AppendRequest(
                List.of(event),
                null
        );

        client.handle(appendRequest);
        long newHeadPosition = client.getHeadPosition();

        // Ensure the head position has been updated
        assertTrue(newHeadPosition > headPosition, "Head position should increase after appending a new event!");
        System.out.println("New head position: " + newHeadPosition);
    }

    @Test
    void testSubscribeToEvents() {
        var readRequest = new ReadRequest(
                null, // no specific query, just subscribe to all events
                0L, // starting position
                false, // not backwards
                10, // limit number of events
                true, // subscribe flag set to true
                null // batch size not specified
        );

        var iterator = client.handle(readRequest);

        boolean eventReceived = false;
        while (iterator.hasNext()) {
            var response = iterator.next();
            System.out.println("Processing event: " + response.events());
            eventReceived = true;

            // Stop after receiving one event (for simplicity in this test)
            break;
        }

        // Assert that at least one event was received
        assertTrue(eventReceived, "No events received during subscription!");
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        client.shutdown();
    }

}
