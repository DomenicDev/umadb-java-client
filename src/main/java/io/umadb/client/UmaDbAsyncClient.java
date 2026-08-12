package io.umadb.client;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Non-blocking counterpart to {@link UmaDbClient}.
 * <p>
 * Unary operations return a {@link CompletableFuture}; the two server-streaming
 * operations deliver their batches to a {@link UmaDbStreamObserver} and return a
 * {@link UmaDbStream} handle for flow control and cancellation.
 *
 * <pre>{@code
 * UmaDbAsyncClient client = UmaDbClient.builder()
 *     .withHostAndPort("localhost", 50051)
 *     .buildAsync();
 *
 * client.connect();
 * }</pre>
 *
 * <h2>Error handling</h2>
 * <p>
 * Unlike the blocking client, which throws {@link UmaDbException} directly, failures here
 * arrive <strong>wrapped</strong>: a future completes exceptionally with a
 * {@link java.util.concurrent.CompletionException} (or
 * {@link java.util.concurrent.ExecutionException} from {@code get()}) whose cause is the
 * {@code UmaDbException}. Streaming failures are delivered unwrapped to
 * {@link UmaDbStreamObserver#onError(UmaDbException)}.
 * <p>
 * Argument validation is the exception to that rule: passing a null observer or a blank
 * source throws immediately, because those are programming errors rather than remote
 * failures.
 *
 * <h2>Threading</h2>
 * <p>
 * Futures are completed on a gRPC callback thread, so any continuation attached with
 * {@code thenApply} and friends runs there too. Use the {@code *Async} variants with your
 * own executor for anything expensive. The same applies to
 * {@link UmaDbStreamObserver} callbacks.
 *
 * @see UmaDbClientBuilder#buildAsync()
 */
public interface UmaDbAsyncClient extends AutoCloseable {

    /**
     * Establishes a connection to the UmaDB server.
     * <p>
     * This method must be called before performing any operations. It is idempotent.
     *
     * @throws UmaDbException if the connection cannot be established
     */
    void connect();

    /**
     * Appends events to the event store.
     *
     * @param appendRequest the request describing the events to append
     * @return a future completing with the position of the last appended event, or
     *         completing exceptionally with a {@link UmaDbException} cause
     */
    CompletableFuture<AppendResponse> handle(AppendRequest appendRequest);

    /**
     * Returns the position of the most recent event in the event store.
     *
     * @return a future completing with the sequence number of the latest event
     */
    CompletableFuture<Long> getHeadPosition();

    /**
     * Returns the last recorded position for a named source.
     *
     * @param source the unique identifier of the consumer or projection
     * @return a future completing with the position the source has processed up to, or
     *         {@link Optional#empty()} if no tracking info exists for it
     * @throws IllegalArgumentException if {@code source} is {@code null} or blank
     */
    CompletableFuture<Optional<Long>> getTrackingInfo(String source);

    /**
     * Reads events, delivering each batch to the given observer.
     * <p>
     * The stream ends, with {@link UmaDbStreamObserver#onCompleted()}, once the head
     * position captured when the request was received has been reached. To keep receiving
     * events as they are appended, use {@link #subscribe} instead.
     *
     * @param readRequest the request describing which events to read
     * @param observer    receives the batches; must not be {@code null}
     * @return a handle for controlling demand and cancelling the stream
     * @throws IllegalArgumentException if {@code observer} is {@code null}
     */
    UmaDbStream handle(ReadRequest readRequest, UmaDbStreamObserver<ReadResponse> observer);

    /**
     * Opens a continuous subscription, delivering each batch to the given observer.
     * <p>
     * The subscription replays already-recorded events matching the request and then
     * continues to deliver new events as they are appended. It does not end on its own —
     * call {@link UmaDbStream#cancel()} or {@link #shutdown()} to stop it.
     *
     * @param subscribeRequest the request describing which events to subscribe to
     * @param observer         receives the batches; must not be {@code null}
     * @return a handle for controlling demand and cancelling the subscription
     * @throws IllegalArgumentException if {@code observer} is {@code null}
     */
    UmaDbStream subscribe(SubscribeRequest subscribeRequest, UmaDbStreamObserver<SubscribeResponse> observer);

    /**
     * Shuts the client down, cancelling any live streams and closing the connection.
     * <p>
     * Idempotent. After calling this the client should not be used again; further calls
     * fail through the returned future or the observer rather than by throwing.
     *
     * @throws UmaDbException if the connection cannot be closed properly
     */
    void shutdown();

    /**
     * Equivalent to {@link #shutdown()}, so the client can be used in a
     * try-with-resources block.
     */
    @Override
    default void close() {
        shutdown();
    }
}
