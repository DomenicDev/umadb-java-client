package io.umadb.client;

/**
 * A handle on an active server stream, used to control demand and to cancel the stream.
 * <p>
 * An instance is handed to {@link UmaDbStreamObserver#onStart(UmaDbStream)} before any
 * event is delivered, and is also returned from the call that opened the stream.
 *
 * <h2>Flow control</h2>
 * <p>
 * By default a stream runs in <em>push mode</em>: the client requests one batch at a time
 * and automatically asks for the next one once
 * {@link UmaDbStreamObserver#onNext(Object)} returns. Nothing is buffered, so a slow
 * consumer naturally slows the server down.
 * <p>
 * Calling {@link #request(long)} from within
 * {@link UmaDbStreamObserver#onStart(UmaDbStream)} switches the stream to <em>pull
 * mode</em>: no batch is delivered unless it has been requested, and the consumer becomes
 * responsible for asking for more. This is useful when a consumer wants to control exactly
 * how much data is in flight.
 *
 * <h2>Memory</h2>
 * <p>
 * The events held in memory at any moment are roughly
 * {@code outstanding demand × batch size}, where the batch size is the one configured on
 * the {@link ReadRequest} or {@link SubscribeRequest}. Requesting very large amounts on a
 * subscription, which never ends, is how you run out of memory.
 *
 * <h2>Thread safety</h2>
 * <p>
 * Both methods are safe to call from any thread.
 */
public interface UmaDbStream {

    /**
     * Requests {@code count} further batches.
     * <p>
     * Demand is additive. Calling this for the first time from
     * {@link UmaDbStreamObserver#onStart(UmaDbStream)} switches the stream to pull mode.
     *
     * @param count the number of additional batches to deliver; must be &gt; 0
     * @throws IllegalArgumentException if {@code count} is not positive
     */
    void request(long count);

    /**
     * Cancels the stream.
     * <p>
     * Idempotent, and safe to call from any thread, including from inside a callback.
     * After cancellation at most one further terminal signal is delivered to the observer.
     */
    void cancel();

    /**
     * Returns whether the stream is still running, i.e. it has neither completed, nor
     * failed, nor been cancelled.
     *
     * @return {@code true} while the stream is live
     */
    boolean isActive();
}
