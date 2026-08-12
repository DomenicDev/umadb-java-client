package io.umadb.client.grpc;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.umadb.client.UmaDbException;

import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Owns the {@link ManagedChannel} lifecycle for a client.
 * <p>
 * State transitions are atomic because an asynchronous client is routinely shared across
 * threads, and gRPC callbacks may observe this connection from its own callback threads.
 * <p>
 * Package-private internal machinery shared by the blocking and asynchronous clients.
 */
final class GrpcConnection {

    /**
     * Maximum time to wait for a graceful channel shutdown.
     */
    private static final int TIMEOUT_TERMINATION_SECONDS = 15;

    /**
     * Maximum time to wait after a forced shutdown.
     */
    private static final int TIMEOUT_FORCED_TERMINATION_SECONDS = 5;

    private enum State {NEW, CONNECTED, SHUTDOWN}

    private final ConnectionSettings settings;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private final AtomicReference<ManagedChannel> channel = new AtomicReference<>();

    GrpcConnection(ConnectionSettings settings) {
        this.settings = settings;
    }

    /**
     * Establishes the channel. Idempotent: a second call is a no-op.
     *
     * @throws UmaDbException.IoException if the channel cannot be created
     */
    void connect() {
        if (!state.compareAndSet(State.NEW, State.CONNECTED)) {
            return;
        }
        try {
            channel.set(ChannelFactory.newChannel(settings));
        } catch (Exception e) {
            state.set(State.NEW);
            throw new UmaDbException.IoException(
                    "Failed to connect to UmaDB: " + e.getMessage()
            );
        }
    }

    /**
     * Returns the channel to issue calls on.
     *
     * @throws IllegalStateException if {@code connect()} has not been called
     */
    Channel channel() {
        var current = channel.get();
        if (current == null) {
            throw new IllegalStateException("connect() must be called before using the client");
        }
        return current;
    }

    boolean isConnected() {
        return state.get() == State.CONNECTED;
    }

    boolean isShutdown() {
        return state.get() == State.SHUTDOWN;
    }

    /**
     * Shuts the channel down. Idempotent, and a no-op if never connected.
     * <p>
     * {@link ManagedChannel#shutdown()} is graceful: calls already in flight keep running.
     * A server stream that never ends on its own, such as a subscription, would therefore
     * hold the channel open for the whole grace period and leave its transport threads
     * behind. So live calls are cancelled first, and a forced shutdown is used as a
     * fallback if the graceful one does not complete in time.
     *
     * @param beforeShutdown run before the channel is closed, so that live streams can be
     *                       cancelled first; may be {@code null}
     */
    void shutdown(Runnable beforeShutdown) {
        if (!state.compareAndSet(State.CONNECTED, State.SHUTDOWN)) {
            return;
        }
        if (beforeShutdown != null) {
            beforeShutdown.run();
        }
        var current = channel.get();
        if (current == null) {
            // connect() had claimed the state but not yet published the channel
            return;
        }
        try {
            if (!current.shutdown().awaitTermination(TIMEOUT_TERMINATION_SECONDS, SECONDS)) {
                current.shutdownNow().awaitTermination(TIMEOUT_FORCED_TERMINATION_SECONDS, SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UmaDbException(e.getMessage(), e);
        }
    }
}
