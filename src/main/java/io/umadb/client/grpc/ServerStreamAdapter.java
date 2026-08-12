package io.umadb.client.grpc;

import io.grpc.Context;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.umadb.client.UmaDbStream;
import io.umadb.client.UmaDbStreamObserver;

import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Bridges a gRPC server stream onto a {@link UmaDbStreamObserver}, and doubles as the
 * {@link UmaDbStream} handle returned to the caller.
 *
 * <h2>Flow control</h2>
 * <p>
 * In push mode the stream runs with an initial demand of one and asks for the next batch
 * only once the consumer's {@code onNext} has returned, so nothing is ever buffered on our
 * side and a slow consumer throttles the server. That matters most for subscriptions,
 * which never end: any unbounded buffering there is an eventual out-of-memory.
 * <p>
 * Requesting demand from {@link UmaDbStreamObserver#onStart(UmaDbStream)} switches the
 * stream to pull mode, where the consumer drives delivery entirely.
 *
 * <h2>Cancellation</h2>
 * <p>
 * Cancellation goes through the call's {@link io.grpc.Context.CancellableContext} rather
 * than {@link ClientCallStreamObserver#cancel}, because the latter is only safe on the
 * call's own thread whereas the context is explicitly safe to cancel from anywhere.
 *
 * @param <Req> the protobuf request type
 * @param <Res> the protobuf response type
 * @param <T>   the domain type delivered to the consumer
 */
final class ServerStreamAdapter<Req, Res, T>
        implements ClientResponseObserver<Req, Res>, UmaDbStream {

    private final UmaDbStreamObserver<T> downstream;
    private final Function<Res, T> mapper;
    private final Context.CancellableContext callContext;
    private final Set<ServerStreamAdapter<?, ?, ?>> registry;

    private final AtomicBoolean terminated = new AtomicBoolean();
    private final AtomicLong demandBeforeStart = new AtomicLong();
    private final AtomicReference<ClientCallStreamObserver<Req>> upstream = new AtomicReference<>();

    private volatile boolean started;
    private volatile boolean pullMode;

    ServerStreamAdapter(
            UmaDbStreamObserver<T> downstream,
            Function<Res, T> mapper,
            Context.CancellableContext callContext,
            Set<ServerStreamAdapter<?, ?, ?>> registry
    ) {
        this.downstream = downstream;
        this.mapper = mapper;
        this.callContext = callContext;
        this.registry = registry;
    }

    // ------------------------------------------------------------------
    // ClientResponseObserver
    // ------------------------------------------------------------------

    @Override
    public void beforeStart(ClientCallStreamObserver<Req> requestStream) {
        upstream.set(requestStream);

        // Give the consumer its handle before anything can arrive. This is the only point
        // at which initial demand can be expressed, since by the time the opening call
        // returns, batches may already have been delivered.
        try {
            downstream.onStart(this);
        } catch (RuntimeException e) {
            failAndCancel(e);
            return;
        }

        if (terminated.get()) {
            return;
        }

        // request() may not be called before the call starts, so any demand expressed in
        // onStart has to be folded into the initial value here.
        long initial = demandBeforeStart.getAndSet(0);
        requestStream.disableAutoRequestWithInitial(pullMode ? clampToInt(initial) : 1);
        started = true;

        // Cover demand that arrived from another thread while we were starting.
        long late = demandBeforeStart.getAndSet(0);
        if (late > 0) {
            requestStream.request(clampToInt(late));
        }
    }

    @Override
    public void onNext(Res value) {
        if (terminated.get()) {
            return;
        }

        T mapped;
        try {
            mapped = mapper.apply(value);
        } catch (RuntimeException e) {
            // A mapping failure must not escape: gRPC would cancel the call and hand us
            // back its own CANCELLED status, destroying the real cause.
            failAndCancel(e);
            return;
        }

        try {
            downstream.onNext(mapped);
        } catch (RuntimeException e) {
            failAndCancel(e);
            return;
        }

        if (!pullMode && !terminated.get()) {
            var current = upstream.get();
            if (current != null) {
                current.request(1);
            }
        }
    }

    @Override
    public void onError(Throwable t) {
        if (terminated.compareAndSet(false, true)) {
            deregister();
            downstream.onError(GrpcErrorTranslator.translate(t));
        }
    }

    @Override
    public void onCompleted() {
        if (terminated.compareAndSet(false, true)) {
            deregister();
            downstream.onCompleted();
        }
    }

    // ------------------------------------------------------------------
    // UmaDbStream
    // ------------------------------------------------------------------

    @Override
    public void request(long count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
        if (terminated.get()) {
            return;
        }

        if (!started) {
            // Demand expressed from onStart selects pull mode.
            pullMode = true;
            demandBeforeStart.addAndGet(count);
            return;
        }

        var current = upstream.get();
        if (current != null) {
            // Documented thread-safe, so no external synchronization is needed here.
            current.request(clampToInt(count));
        }
    }

    @Override
    public void cancel() {
        if (terminated.compareAndSet(false, true)) {
            deregister();
            callContext.cancel(new CancellationException("Stream cancelled by caller"));
        }
    }

    @Override
    public boolean isActive() {
        return !terminated.get();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Reports a local failure to the consumer and tears the call down.
     */
    private void failAndCancel(Throwable cause) {
        if (terminated.compareAndSet(false, true)) {
            deregister();
            try {
                downstream.onError(GrpcErrorTranslator.translate(cause));
            } finally {
                // Any resulting CANCELLED callback is dropped, since we are already terminated.
                callContext.cancel(cause);
            }
        }
    }

    private void deregister() {
        if (registry != null) {
            registry.remove(this);
        }
    }

    private static int clampToInt(long count) {
        return (int) Math.min(count, Integer.MAX_VALUE);
    }
}
