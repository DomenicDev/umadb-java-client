package io.umadb.client;

import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    private UmaDbClient client;

    @BeforeEach
    void setUp() {
        client = UmaDbClient.builder()
                .withHost(UMA_DB_CONTAINER.getHost())
                .withPort(UMA_DB_CONTAINER.getExposedGrpcPort())
                .build();

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
        ReadRequest readRequest = new ReadRequest(null, 0L, null, null, null);
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
    void testSubscribeReceivesEventsAppendedAfterSubscription() throws Exception {
        // Start subscription from current head (no historical events)
        long startPosition = client.getHeadPosition();

        SubscribeRequest subscribeRequest = SubscribeRequest.all().after(startPosition);

        Iterator<SubscribeResponse> iterator = client.subscribe(subscribeRequest);
        assertNotNull(iterator, "Iterator must not be null");

        // Synchronization primitives
        CountDownLatch eventReceivedLatch = new CountDownLatch(1);
        AtomicReference<SubscribeResponse> receivedResponse = new AtomicReference<>();

        // Consume the stream asynchronously
        Thread subscriberThread = new Thread(() -> {
            while (iterator.hasNext()) {
                SubscribeResponse response = iterator.next();
                if (!response.events().isEmpty()) {
                    receivedResponse.set(response);
                    eventReceivedLatch.countDown();
                    break; // we only care about the first streamed event
                }
            }
        });

        subscriberThread.start();

        // Append a new event AFTER subscription started
        Event event = Event.of(
                "stream-test",
                List.of("live"),
                "streamed-event".getBytes(UTF_8)
        );

        client.handle(new AppendRequest(List.of(event), null));

        // Wait for the event to arrive
        boolean received = eventReceivedLatch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Subscriber did not receive appended event in time");

        SubscribeResponse response = receivedResponse.get();
        assertNotNull(response);
        assertEquals(1, response.events().size());

        var receivedEvent = response.events().getFirst();
        assertEquals("stream-test", receivedEvent.event().type());
    }

    @Test
    @Order(7)
    void testEventMetadataRoundTrips() {
        Event event = Event.of("metadata-test", List.of("meta"), "payload".getBytes(UTF_8))
                .withMetadata("correlation-id", "abc-123")
                .withMetadata("user-agent", "umadb-java-client");

        long position = client.handle(new AppendRequest(List.of(event), null)).position();

        ReadRequest readRequest = new ReadRequest(
                createQuery(createQueryItem(List.of("metadata-test"), List.of("meta"))),
                position,
                null,
                1,
                null
        );

        var iterator = client.handle(readRequest);
        assertTrue(iterator.hasNext(), "Expected the appended event to be readable");

        var events = iterator.next().events();
        assertEquals(1, events.size());

        Event readBack = events.getFirst().event();
        assertEquals(
                Map.of("correlation-id", "abc-123", "user-agent", "umadb-java-client"),
                readBack.metadata(),
                "Metadata should round-trip through the server"
        );
    }

    @Test
    @Order(8)
    void testTrackingInfoIsCheckpointedOnAppend() {
        String source = "projection-" + UUID.randomUUID();

        assertEquals(Optional.empty(), client.getTrackingInfo(source),
                "An unknown source should have no tracking info");

        Event event = createEvent("tracking-test", List.of("tracked"), "payload");
        long checkpoint = client.getHeadPosition();

        client.handle(
                new AppendRequest(List.of(event), null)
                        .withTrackingInfo(TrackingInfo.of(source, checkpoint))
        );

        assertEquals(Optional.of(checkpoint), client.getTrackingInfo(source),
                "Tracking cursor should advance with the append");
    }

}
