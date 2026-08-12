package io.umadb.client;

/**
 * Receives the events of an asynchronous server stream.
 *
 * <h2>Signal ordering</h2>
 * <p>
 * For any one stream the callbacks are invoked in order and never concurrently:
 * {@link #onStart(UmaDbStream)} first, then zero or more {@link #onNext(Object)}, then
 * exactly one terminal signal — either {@link #onCompleted()} or
 * {@link #onError(UmaDbException)}, never both.
 *
 * <h2>Threading</h2>
 * <p>
 * Callbacks are invoked on a gRPC callback thread. <strong>Do not block and do not perform
 * long-running work in them</strong>: doing so holds up the stream and occupies a thread
 * from the channel's executor. Hand work off to your own executor if it is not trivial.
 * Note that blocking on another UmaDB call from inside a callback risks deadlock if the
 * channel's executor is saturated.
 * <p>
 * An exception thrown from {@link #onNext(Object)} cancels the stream and is reported back
 * through {@link #onError(UmaDbException)}.
 *
 * @param <T> the type of value delivered, e.g. {@link ReadResponse} or {@link SubscribeResponse}
 */
public interface UmaDbStreamObserver<T> {

    /**
     * Invoked once, before the stream starts and before any value is delivered.
     * <p>
     * This is the only place where initial demand can be set, which is why it exists: by
     * the time the opening call returns its {@link UmaDbStream}, values may already have
     * arrived. Call {@link UmaDbStream#request(long)} here to run the stream in pull mode;
     * do nothing to accept the default push mode.
     *
     * @param stream the handle for controlling this stream
     */
    default void onStart(UmaDbStream stream) {
        // default: push mode, no explicit demand
    }

    /**
     * Invoked for each batch delivered by the server.
     *
     * @param value the batch
     */
    void onNext(T value);

    /**
     * Invoked when the stream fails. Terminal.
     *
     * @param error the failure, already translated into the {@link UmaDbException} hierarchy
     */
    void onError(UmaDbException error);

    /**
     * Invoked when the server has closed the stream normally. Terminal.
     * <p>
     * A subscription opened with {@link UmaDbAsyncClient#subscribe} does not end on its
     * own, so in practice this fires only after {@link UmaDbStream#cancel()} or a shutdown.
     */
    void onCompleted();
}
