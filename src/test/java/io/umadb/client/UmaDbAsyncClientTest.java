package io.umadb.client;

import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link UmaDbAsyncClient} using testcontainers.
 * <p>
 * Uses its own container so that the ordered position assertions in
 * {@link UmaDbClientTest} are not disturbed.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UmaDbAsyncClientTest {

    private static final int TIMEOUT_SECONDS = 5;

    /**
     * How long to watch for a delivery that must not happen.
     */
    private static final long QUIET_PERIOD_MILLIS = 500;

    @Container
    private static final UmaDbContainer UMA_DB_CONTAINER = new UmaDbContainer();

    private UmaDbAsyncClient client;

    @BeforeEach
    void setUp() {
        client = UmaDbClient.builder()
                .withHost(UMA_DB_CONTAINER.getHost())
                .withPort(UMA_DB_CONTAINER.getExposedGrpcPort())
                .buildAsync();

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

    private <T> T await(java.util.concurrent.CompletableFuture<T> future) throws Exception {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // ----------------------
    // Tests
    // ----------------------

    @Test
    @Order(1)
    void testAppendAndHeadPosition() throws Exception {
        Event event = createEvent("async-test", List.of("tag1"), "Hello async");

        AppendResponse response = await(client.handle(AppendRequest.of(List.of(event))));

        assertNotNull(response);
        assertTrue(response.position() > 0, "Position should be greater than 0");
        assertEquals(response.position(), await(client.getHeadPosition()),
                "Head position should match last appended position");
    }

    @Test
    @Order(2)
    void testConditionalAppendFailureIsWrappedInExecutionException() throws Exception {
        Query query = Query.of(QueryItem.of(List.of("async-conditional"), List.of("tagA")));
        long lastPosition = await(client.getHeadPosition());

        AppendCondition condition = AppendCondition.failIfExistsAfter(query, lastPosition);

        await(client.handle(new AppendRequest(
                List.of(createEvent("async-conditional", List.of("tagA"), "first")),
                condition
        )));

        // Second append against the same boundary must fail
        var conflicting = client.handle(new AppendRequest(
                List.of(createEvent("async-conditional", List.of("tagA"), "second")),
                condition
        ));

        ExecutionException thrown = assertThrows(ExecutionException.class, () -> await(conflicting));
        assertInstanceOf(UmaDbException.IntegrityException.class, thrown.getCause(),
                "Async failures must arrive wrapped, with the UmaDbException as the cause");
    }

    @Test
    @Order(3)
    void testGetTrackingInfoForUnknownSourceIsEmpty() throws Exception {
        Optional<Long> position = await(client.getTrackingInfo("unknown-" + UUID.randomUUID()));

        assertEquals(Optional.empty(), position);
    }

    @Test
    @Order(4)
    void testTrackingInfoRoundTrips() throws Exception {
        String source = "async-projection-" + UUID.randomUUID();
        long checkpoint = await(client.getHeadPosition());

        await(client.handle(
                AppendRequest.of(List.of(createEvent("async-tracked", List.of("tracked"), "payload")))
                        .withTrackingInfo(TrackingInfo.of(source, checkpoint))
        ));

        assertEquals(Optional.of(checkpoint), await(client.getTrackingInfo(source)));
    }

    @Test
    @Order(5)
    void testCallAfterShutdownFailsWithoutHanging() {
        client.shutdown();

        var future = client.handle(AppendRequest.of(
                List.of(createEvent("after-shutdown", List.of("tag"), "payload"))
        ));

        ExecutionException thrown = assertThrows(ExecutionException.class, () -> await(future));
        assertInstanceOf(UmaDbException.IoException.class, thrown.getCause());
    }

    @Test
    @Order(6)
    void testBlankSourceThrowsSynchronously() {
        assertThrows(IllegalArgumentException.class, () -> client.getTrackingInfo("  "),
                "Argument validation is a programming error and must throw, not fail the future");
    }

    @Test
    @Order(7)
    void testNullObserverThrowsSynchronously() {
        var request = SubscribeRequest.all();
        assertThrows(IllegalArgumentException.class, () -> client.subscribe(request, null));
    }

    @Test
    @Order(8)
    void testReadStreamCompletes() throws Exception {
        await(client.handle(AppendRequest.of(
                List.of(createEvent("async-read", List.of("readable"), "payload"))
        )));

        var observer = new CollectingObserver<ReadResponse>();
        client.handle(ReadRequest.all(), observer);

        assertTrue(observer.awaitTermination(), "Read stream should complete");
        assertEquals(1, observer.completions.get(), "onCompleted must fire exactly once");
        assertEquals(0, observer.errors.size(), "A successful read must not report an error");
        assertTrue(observer.totalEvents() > 0, "Read should deliver at least one event");
    }

    @Test
    @Order(9)
    void testSubscribeReceivesEventsAppendedAfterSubscription() throws Exception {
        long startPosition = await(client.getHeadPosition());

        var observer = new CollectingObserver<SubscribeResponse>();
        UmaDbStream stream = client.subscribe(SubscribeRequest.all().after(startPosition), observer);

        await(client.handle(AppendRequest.of(
                List.of(createEvent("async-stream", List.of("live"), "streamed"))
        )));

        assertTrue(observer.awaitValue(), "Subscriber did not receive the appended event in time");
        assertTrue(stream.isActive(), "Subscription should still be running");

        stream.cancel();
        assertFalse(stream.isActive(), "Subscription should be inactive once cancelled");
    }

    @Test
    @Order(10)
    void testCancelStopsDelivery() throws Exception {
        long startPosition = await(client.getHeadPosition());

        var observer = new CollectingObserver<SubscribeResponse>();
        UmaDbStream stream = client.subscribe(SubscribeRequest.all().after(startPosition), observer);

        await(client.handle(AppendRequest.of(
                List.of(createEvent("async-cancel", List.of("live"), "first"))
        )));
        assertTrue(observer.awaitValue(), "Expected the first event before cancelling");

        stream.cancel();
        int deliveredAtCancel = observer.values.size();
        observer.resetValueLatch();

        // Anything appended after cancellation must not arrive
        await(client.handle(AppendRequest.of(
                List.of(createEvent("async-cancel", List.of("live"), "second"))
        )));

        assertTrue(observer.awaitSilence(), "No batches may arrive after cancel()");
        assertEquals(deliveredAtCancel, observer.values.size(), "No batches may arrive after cancel()");
        assertEquals(0, observer.completions.get() + observer.errors.size(),
                "Cancelling delivers no further terminal signal");
    }

    @Test
    @Order(11)
    void testExplicitDemandDeliversOnlyWhatWasRequested() throws Exception {
        // Guarantee several batches are available by using a batch size of one
        for (int i = 0; i < 3; i++) {
            await(client.handle(AppendRequest.of(
                    List.of(createEvent("async-demand", List.of("demand"), "payload-" + i))
            )));
        }

        var observer = new CollectingObserver<ReadResponse>();
        observer.initialDemand = 1;

        UmaDbStream stream = client.handle(
                ReadRequest.of(Query.of(QueryItem.of(List.of("async-demand"), List.of("demand"))))
                        .withBatchSize(1),
                observer
        );

        assertTrue(observer.awaitValue(), "Expected exactly one batch for the initial demand");

        observer.resetValueLatch();
        assertTrue(observer.awaitSilence(), "Pull mode must not deliver more than was requested");
        assertEquals(1, observer.values.size(), "Pull mode must not deliver more than was requested");

        stream.request(1);
        assertTrue(observer.awaitValue(), "Expected a second batch after requesting more");
        assertEquals(2, observer.values.size());

        stream.cancel();
    }

    @Test
    @Order(12)
    void testObserverExceptionSurfacesAsUmaDbError() throws Exception {
        long startPosition = await(client.getHeadPosition());

        var failure = new java.util.concurrent.CountDownLatch(1);
        var reported = new java.util.concurrent.atomic.AtomicReference<UmaDbException>();

        client.subscribe(SubscribeRequest.all().after(startPosition), new UmaDbStreamObserver<>() {
            @Override
            public void onNext(SubscribeResponse value) {
                throw new IllegalStateException("boom");
            }

            @Override
            public void onError(UmaDbException error) {
                reported.set(error);
                failure.countDown();
            }

            @Override
            public void onCompleted() {
                // not expected
            }
        });

        await(client.handle(AppendRequest.of(
                List.of(createEvent("async-throwing", List.of("live"), "payload"))
        )));

        assertTrue(failure.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "onError should have been invoked");
        assertNotNull(reported.get());
        assertTrue(reported.get().getMessage().contains("boom"),
                "The consumer's own failure must surface, not a masking CANCELLED status; got: "
                        + reported.get().getMessage());
    }

    @Test
    @Order(13)
    void testShutdownWithOpenSubscriptionReturnsPromptly() throws Exception {
        long startPosition = await(client.getHeadPosition());
        client.subscribe(SubscribeRequest.all().after(startPosition), new CollectingObserver<>());

        // A subscription never ends on its own, so an unguarded graceful shutdown would
        // block for the full termination timeout.
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> client.shutdown());
    }

    /**
     * Observer that records everything it is given, for assertions.
     */
    private static final class CollectingObserver<T> implements UmaDbStreamObserver<T> {

        private final java.util.List<T> values = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        private final java.util.List<UmaDbException> errors =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        private final java.util.concurrent.atomic.AtomicInteger completions =
                new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.CountDownLatch terminal = new java.util.concurrent.CountDownLatch(1);

        private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CountDownLatch> value =
                new java.util.concurrent.atomic.AtomicReference<>(new java.util.concurrent.CountDownLatch(1));
        private int initialDemand;

        @Override
        public void onStart(UmaDbStream stream) {
            if (initialDemand > 0) {
                stream.request(initialDemand);
            }
        }

        @Override
        public void onNext(T next) {
            values.add(next);
            value.get().countDown();
        }

        @Override
        public void onError(UmaDbException error) {
            errors.add(error);
            terminal.countDown();
        }

        @Override
        public void onCompleted() {
            completions.incrementAndGet();
            terminal.countDown();
        }

        boolean awaitTermination() throws InterruptedException {
            return terminal.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        boolean awaitValue() throws InterruptedException {
            return value.get().await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        /**
         * Waits a short quiet period and reports whether the stream stayed silent.
         * <p>
         * Returns as soon as a batch does arrive, so an unwanted delivery fails the test
         * immediately rather than after the full wait.
         *
         * @return {@code true} if no batch was delivered during the quiet period
         */
        boolean awaitSilence() throws InterruptedException {
            return !value.get().await(QUIET_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        }

        void resetValueLatch() {
            value.set(new java.util.concurrent.CountDownLatch(1));
        }

        int totalEvents() {
            synchronized (values) {
                return values.stream()
                        .mapToInt(v -> v instanceof ReadResponse r ? r.events().size()
                                : v instanceof SubscribeResponse s ? s.events().size() : 0)
                        .sum();
            }
        }
    }
}
