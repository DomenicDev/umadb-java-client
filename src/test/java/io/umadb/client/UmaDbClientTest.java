package io.umadb.client;

import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UmaDbClient using testcontainers.
 * <p>
 * Tests are executed in order to ensure predictable event positions.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UmaDbClientTest {

    @Container
    private static final UmaDbContainer UMA_DB_CONTAINER = new UmaDbContainer();

    private UmaDbClientImpl client;

    @BeforeEach
    void setUp() {
        client = new UmaDbClientImpl(
                UMA_DB_CONTAINER.getHost(),
                UMA_DB_CONTAINER.getExposedGrpcPort()
        );
        client.connect();
    }

    @AfterEach
    void tearDown() {
        client.shutdown();
    }

    // ----------------------
    // Helper Methods
    // ----------------------

    private Event createEvent(String type, List<String> tags, String payload) {
        return Event.of(type, tags, payload.getBytes(UTF_8));
    }

    private AppendCondition conditional(Query query, Long after) {
        return AppendCondition.failIfExistsAfter(query, after != null ? after : 0L);
    }

    private QueryItem createQueryItem(List<String> types, List<String> tags) {
        return QueryItem.of(types, tags);
    }

    private Query createQuery(QueryItem... items) {
        return new Query(List.of(items));
    }

    // ----------------------
    // Tests
    // ----------------------

    @Test
    @Order(1)
    void testSimpleAppendAndHeadPosition() {
        Event event = Event.of("test-type", List.of("tag1"), "Hello123".getBytes(UTF_8));
        AppendRequest request = new AppendRequest(List.of(event), null);

        AppendResponse response = client.handle(request);

        assertNotNull(response, "AppendResponse should not be null");
        assertTrue(response.position() > 0, "Position should be greater than 0");
        assertEquals(response.position(), client.getHeadPosition(), "Head position should match last appended position");
    }

    @Test
    @Order(2)
    void testConditionalAppendIntegrityException() {
        QueryItem queryItem = createQueryItem(List.of("example"), List.of("tag1", "tag2"));
        Query query = createQuery(queryItem);
        long lastPosition = client.getHeadPosition();

        Event firstEvent = createEvent("example", List.of("tag1", "tag2"), "Hello, World!");
        AppendRequest appendRequest1 = new AppendRequest(
                List.of(firstEvent),
                conditional(query, lastPosition)
        );

        // First append succeeds
        AppendResponse response1 = client.handle(appendRequest1);
        assertTrue(response1.position() > lastPosition);

        // Second append with same boundary should fail
        Event conflictingEvent = createEvent("example", List.of("tag1", "tag2"), "Hello, World!");
        AppendRequest appendRequest2 = new AppendRequest(
                List.of(conflictingEvent),
                conditional(query, lastPosition)
        );

        assertThrows(UmaDbException.IntegrityException.class,
                () -> client.handle(appendRequest2),
                "Appending conflicting event should throw IntegrityException");
    }

    @Test
    @Order(3)
    void testIdempotentAppendReturnsSamePosition() {
        QueryItem queryItem = createQueryItem(List.of("example"), List.of("tag1", "tag2"));
        Query query = createQuery(queryItem);
        long lastPosition = client.getHeadPosition();

        Event event = createEvent("example", List.of("tag1", "tag2"), "Hello, World!");
        AppendRequest appendRequest1 = new AppendRequest(
                List.of(event),
                conditional(query, lastPosition)
        );

        AppendResponse response1 = client.handle(appendRequest1);

        // Retry with the same event
        AppendRequest appendRequest2 = new AppendRequest(
                List.of(event),
                conditional(query, lastPosition)
        );

        AppendResponse response2 = client.handle(appendRequest2);

        assertEquals(response1.position(), response2.position(), "Retry should return same position (idempotent)");
    }

    @Test
    @Order(4)
    void testGetHeadPositionUpdatesAfterAppend() {
        long initialHead = client.getHeadPosition();

        Event event = createEvent("example", List.of("tag1", "tag2"), "New Event");
        AppendRequest appendRequest = new AppendRequest(List.of(event), null);
        client.handle(appendRequest);

        long newHead = client.getHeadPosition();
        assertTrue(newHead > initialHead, "Head position should increase after appending a new event");
    }

    @Test
    @Order(5)
    void testReadEvents() {
        ReadRequest readRequest = new ReadRequest(null, 0L, null, null, null, null);
        var iterator = client.handle(readRequest);

        assertNotNull(iterator, "Iterator should not be null");

        boolean hasEvents = false;
        while (iterator.hasNext()) {
            ReadResponse response = iterator.next();
            response.events().forEach(se -> {
                assertNotNull(se.event());
                assertTrue(se.position() >= 0);
            });
            hasEvents = true;
        }

        assertTrue(hasEvents, "Read request should return at least one event");
    }

    @Test
    @Order(6)
    void testSubscribeToEventsReceivesAtLeastOne() {
        ReadRequest subscribeRequest = new ReadRequest(null, 0L, false, 10, true, null);
        var iterator = client.handle(subscribeRequest);

        assertNotNull(iterator, "Iterator should not be null");

        boolean eventReceived = false;
        if (iterator.hasNext()) {
            ReadResponse response = iterator.next();
            assertNotNull(response.events());
            eventReceived = !response.events().isEmpty();
        }

        assertTrue(eventReceived, "Subscription should receive at least one event");
    }
}
