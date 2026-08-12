package io.umadb.client.grpc;

import io.grpc.Context;
import io.grpc.StatusRuntimeException;
import io.umadb.client.*;
import umadb.v1.DCBGrpc;
import umadb.v1.Umadb;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Internal gRPC-based implementation of {@link UmaDbClient}.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Establishing and managing the gRPC channel</li>
 *   <li>Configuring TLS and API key authentication</li>
 *   <li>Mapping gRPC responses and errors to client-facing domain objects</li>
 *   <li>Translating server error responses into {@link UmaDbException}s</li>
 * </ul>
 *
 * <p>
 * This class is <strong>not</strong> intended to be used directly by client code.
 * Clients should construct instances via {@link UmaDbClientBuilder}.
 */
public final class UmaDbClientImpl implements UmaDbClient {

    private final GrpcConnection connection;

    /**
     * Contexts of streams still open, so that shutdown can cancel any the caller has
     * stopped consuming.
     */
    private final Set<Context.CancellableContext> liveStreamContexts = ConcurrentHashMap.newKeySet();

    private DCBGrpc.DCBBlockingStub blockingStub;

    /**
     * Creates a new client implementation.
     *
     * @param host       UmaDB server host
     * @param port       UmaDB server port
     * @param tlsEnabled indicates whether an encrypted communication (TLS) is to be used
     * @param caFilePath optional path to a CA certificate for TLS
     * @param apiKey     optional API key (requires TLS)
     * @throws IllegalArgumentException if arguments are invalid or insecure
     */
    public UmaDbClientImpl(
            String host,
            int port,
            boolean tlsEnabled,
            String caFilePath,
            String apiKey
    ) {
        this(host, port, tlsEnabled, caFilePath, apiKey, null);
    }

    /**
     * Creates a new client implementation dispatching gRPC callbacks on the given executor.
     *
     * @param host       UmaDB server host
     * @param port       UmaDB server port
     * @param tlsEnabled indicates whether an encrypted communication (TLS) is to be used
     * @param caFilePath optional path to a CA certificate for TLS
     * @param apiKey     optional API key (requires TLS)
     * @param executor   optional executor for gRPC callbacks; {@code null} uses the gRPC default
     * @throws IllegalArgumentException if arguments are invalid or insecure
     */
    public UmaDbClientImpl(
            String host,
            int port,
            boolean tlsEnabled,
            String caFilePath,
            String apiKey,
            Executor executor
    ) {
        this.connection = new GrpcConnection(
                new ConnectionSettings(host, port, tlsEnabled, caFilePath, apiKey, executor)
        );
    }

    @Override
    public void connect() {
        if (connection.isConnected()) {
            return;
        }
        connection.connect();
        this.blockingStub = DCBGrpc.newBlockingStub(connection.channel());
    }

    @Override
    public AppendResponse handle(AppendRequest appendRequest) {
        var umadbAppendRequest = UmaDbUtils.toUmadbAppendRequest(appendRequest);
        try {
            var umadbAppendResponse = blockingStub.append(umadbAppendRequest);
            return new AppendResponse(umadbAppendResponse.getPosition());
        } catch (StatusRuntimeException e) {
            throw GrpcErrorTranslator.translate(e);
        }
    }

    @Override
    public Iterator<ReadResponse> handle(ReadRequest readRequest) {
        var umadbReadRequest = UmaDbUtils.toUmadbReadRequest(readRequest);
        return openStream(
                stub -> stub.read(umadbReadRequest),
                UmaDbUtils::toReadResponse
        );
    }

    @Override
    public Iterator<SubscribeResponse> subscribe(SubscribeRequest subscribeRequest) {
        var umadbSubscribeRequest = UmaDbUtils.toUmadbSubscribeRequest(subscribeRequest);
        return openStream(
                stub -> stub.subscribe(umadbSubscribeRequest),
                UmaDbUtils::toSubscribeResponse
        );
    }

    /**
     * Opens a blocking server stream inside a cancellable context.
     * <p>
     * The context is registered so that {@link #shutdown()} can cancel a stream the caller
     * has stopped consuming; without that, an abandoned subscription keeps the channel
     * alive for the whole shutdown grace period.
     */
    private <G, T> Iterator<T> openStream(
            Function<DCBGrpc.DCBBlockingStub, Iterator<G>> invoker,
            Function<G, T> mapper
    ) {
        var callContext = Context.current().withCancellation();
        liveStreamContexts.add(callContext);

        Context previous = callContext.attach();
        try {
            return new StreamingIterator<>(invoker.apply(blockingStub), mapper, callContext, liveStreamContexts);
        } catch (StatusRuntimeException e) {
            releaseContext(callContext);
            throw GrpcErrorTranslator.translate(e);
        } finally {
            callContext.detach(previous);
        }
    }

    private void releaseContext(Context.CancellableContext callContext) {
        liveStreamContexts.remove(callContext);
        callContext.close();
    }

    @Override
    public Optional<Long> getTrackingInfo(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be null or blank");
        }
        try {
            var response = blockingStub.getTrackingInfo(UmaDbUtils.toUmadbTrackingRequest(source));
            return response.hasPosition() ? Optional.of(response.getPosition()) : Optional.empty();
        } catch (StatusRuntimeException e) {
            throw GrpcErrorTranslator.translate(e);
        }
    }

    @Override
    public long getHeadPosition() {
        try {
            return blockingStub.head(Umadb.HeadRequest.getDefaultInstance()).getPosition();
        } catch (StatusRuntimeException e) {
            throw GrpcErrorTranslator.translate(e);
        }
    }

    @Override
    public void shutdown() {
        connection.shutdown(this::cancelLiveStreams);
    }

    /**
     * Cancels every stream still open, so the channel has no in-flight calls holding it
     * back. A subscription never ends on its own, so without this the graceful shutdown
     * would block for its full timeout.
     */
    private void cancelLiveStreams() {
        for (var callContext : Set.copyOf(liveStreamContexts)) {
            callContext.cancel(new CancellationException("Client shut down"));
        }
        liveStreamContexts.clear();
    }

    /**
     * Adapts a blocking gRPC response iterator onto the domain type, translating failures
     * and releasing the call's context once the stream is done with.
     */
    private static final class StreamingIterator<G, T> implements Iterator<T> {

        private final Iterator<G> grpcIterator;
        private final Function<G, T> mapper;
        private final Context.CancellableContext callContext;
        private final Set<Context.CancellableContext> registry;

        private StreamingIterator(
                Iterator<G> grpcIterator,
                Function<G, T> mapper,
                Context.CancellableContext callContext,
                Set<Context.CancellableContext> registry
        ) {
            this.grpcIterator = grpcIterator;
            this.mapper = mapper;
            this.callContext = callContext;
            this.registry = registry;
        }

        @Override
        public boolean hasNext() {
            try {
                boolean hasNext = grpcIterator.hasNext();
                if (!hasNext) {
                    release();
                }
                return hasNext;
            } catch (StatusRuntimeException e) {
                release();
                throw GrpcErrorTranslator.translate(e);
            }
        }

        @Override
        public T next() {
            try {
                return mapper.apply(grpcIterator.next());
            } catch (StatusRuntimeException e) {
                release();
                throw GrpcErrorTranslator.translate(e);
            }
        }

        private void release() {
            registry.remove(callContext);
            callContext.close();
        }
    }
}
