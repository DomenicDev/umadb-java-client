package io.umadb.client.grpc;

import io.grpc.Context;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link CompletableFuture} whose cancellation actually cancels the underlying RPC.
 * <p>
 * {@code CompletableFuture} has no notion of an upstream producer: cancelling one merely
 * completes it with a {@link CancellationException} and leaves whatever is computing the
 * value running. For an RPC that means the request keeps going over the wire and the
 * server keeps working on it. Binding the call to a
 * {@link io.grpc.Context.CancellableContext} and cancelling that context in
 * {@link #cancel(boolean)} is what makes cancellation real.
 * <p>
 * Package-private internal machinery.
 *
 * @param <T> the result type
 */
final class UmaDbFuture<T> extends CompletableFuture<T> {

    private final Context.CancellableContext callContext;

    UmaDbFuture(Context.CancellableContext callContext) {
        this.callContext = callContext;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = super.cancel(mayInterruptIfRunning);
        if (cancelled) {
            callContext.cancel(new CancellationException("RPC cancelled by caller"));
        }
        return cancelled;
    }

    /**
     * Returns a plain {@link CompletableFuture} for derived stages.
     * <p>
     * Without this override {@code CompletableFuture} propagates this class to every stage
     * created by {@code thenApply}, {@code thenCompose} and friends, which would mean that
     * cancelling any downstream stage reached back and killed the RPC. Only the future
     * actually returned from the client should have that power.
     */
    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new CompletableFuture<>();
    }
}
