package io.umadb.client.grpc;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import io.umadb.client.*;
import umadb.v1.DCBGrpc;
import umadb.v1.Umadb;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Internal gRPC-based implementation of {@link UmaDbAsyncClient}.
 * <p>
 * Unary calls go through the asynchronous stub rather than the futures stub: the futures
 * stub hands back a Guava {@code ListenableFuture}, which would both leak Guava into the
 * public API and give no way to make cancellation reach the wire. Driving the async stub
 * inside a {@link io.grpc.Context.CancellableContext} solves both, and uses the same
 * cancellation mechanism as the streaming calls.
 * <p>
 * This class is <strong>not</strong> intended to be used directly by client code.
 * Clients should construct instances via {@link UmaDbClientBuilder#buildAsync()}.
 */
public final class UmaDbAsyncClientImpl implements UmaDbAsyncClient {

    private final GrpcConnection connection;

    /**
     * Streams still running, so that shutdown can cancel them before closing the channel.
     */
    private final Set<ServerStreamAdapter<?, ?, ?>> liveStreams = ConcurrentHashMap.newKeySet();

    private DCBGrpc.DCBStub asyncStub;

    /**
     * Creates a new asynchronous client implementation.
     *
     * @param host       UmaDB server host
     * @param port       UmaDB server port
     * @param tlsEnabled indicates whether an encrypted communication (TLS) is to be used
     * @param caFilePath optional path to a CA certificate for TLS
     * @param apiKey     optional API key (requires TLS)
     * @param executor   optional executor for gRPC callbacks; {@code null} uses the gRPC default
     * @throws IllegalArgumentException if arguments are invalid or insecure
     */
    public UmaDbAsyncClientImpl(
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
        this.asyncStub = DCBGrpc.newStub(connection.channel());
    }

    @Override
    public CompletableFuture<AppendResponse> handle(AppendRequest appendRequest) {
        var request = UmaDbUtils.toUmadbAppendRequest(appendRequest);
        return this.<Umadb.AppendResponse, AppendResponse>unaryCall(
                (stub, observer) -> stub.append(request, observer),
                response -> new AppendResponse(response.getPosition())
        );
    }

    @Override
    public CompletableFuture<Long> getHeadPosition() {
        var request = Umadb.HeadRequest.getDefaultInstance();
        return this.<Umadb.HeadResponse, Long>unaryCall(
                (stub, observer) -> stub.head(request, observer),
                Umadb.HeadResponse::getPosition
        );
    }

    @Override
    public CompletableFuture<Optional<Long>> getTrackingInfo(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be null or blank");
        }
        var request = UmaDbUtils.toUmadbTrackingRequest(source);
        return this.<Umadb.TrackingResponse, Optional<Long>>unaryCall(
                (stub, observer) -> stub.getTrackingInfo(request, observer),
                response -> response.hasPosition() ? Optional.of(response.getPosition()) : Optional.empty()
        );
    }

    /**
     * Issues a unary RPC, returning a future whose cancellation cancels the call.
     *
     * @param invoker invokes the RPC on the stub with the given response observer
     * @param mapper  maps the protobuf response onto the domain type
     * @param <G>     the protobuf response type
     * @param <T>     the domain response type
     */
    private <G, T> CompletableFuture<T> unaryCall(
            BiConsumer<DCBGrpc.DCBStub, StreamObserver<G>> invoker,
            Function<G, T> mapper
    ) {
        var unusable = notUsableReason();
        if (unusable != null) {
            return CompletableFuture.failedFuture(unusable);
        }

        var callContext = Context.current().withCancellation();
        var future = new UmaDbFuture<T>(callContext);

        // Binds the call to the cancellable context, so cancelling the future reaches the wire.
        callContext.run(() -> invoker.accept(asyncStub, new StreamObserver<G>() {

            @Override
            public void onNext(G value) {
                try {
                    future.complete(mapper.apply(value));
                } catch (RuntimeException e) {
                    future.completeExceptionally(GrpcErrorTranslator.translate(e));
                }
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(GrpcErrorTranslator.translate(t));
            }

            @Override
            public void onCompleted() {
                // No-op when onNext already completed the future; guards against a server
                // closing a unary call without sending a response.
                future.completeExceptionally(
                        new UmaDbException("Server closed the stream without sending a response")
                );
            }
        }));

        // Releases the context's listener registration once the call terminates.
        future.whenComplete((result, error) -> callContext.close());

        return future;
    }

    /**
     * Returns why the client cannot currently serve calls, or {@code null} if it can.
     * <p>
     * Lifecycle problems are reported through the future or the observer rather than
     * thrown, so that callers have a single error channel for everything except argument
     * validation.
     */
    private UmaDbException notUsableReason() {
        if (connection.isShutdown()) {
            return new UmaDbException.IoException("Client has been shut down");
        }
        if (!connection.isConnected()) {
            return new UmaDbException.IoException("connect() must be called before using the client");
        }
        return null;
    }

    @Override
    public UmaDbStream handle(ReadRequest readRequest, UmaDbStreamObserver<ReadResponse> observer) {
        requireObserver(observer);
        var request = UmaDbUtils.toUmadbReadRequest(readRequest);
        return this.<Umadb.ReadRequest, Umadb.ReadResponse, ReadResponse>startStream(
                observer,
                UmaDbUtils::toReadResponse,
                (stub, adapter) -> stub.read(request, adapter)
        );
    }

    @Override
    public UmaDbStream subscribe(SubscribeRequest subscribeRequest, UmaDbStreamObserver<SubscribeResponse> observer) {
        requireObserver(observer);
        var request = UmaDbUtils.toUmadbSubscribeRequest(subscribeRequest);
        return this.<Umadb.SubscribeRequest, Umadb.SubscribeResponse, SubscribeResponse>startStream(
                observer,
                UmaDbUtils::toSubscribeResponse,
                (stub, adapter) -> stub.subscribe(request, adapter)
        );
    }

    /**
     * Opens a server stream, registering it so that it can be cancelled on shutdown.
     *
     * @param observer receives the mapped batches
     * @param mapper   maps each protobuf response onto the domain type
     * @param invoker  invokes the RPC on the stub with the given adapter as observer
     */
    private <Req, Res, T> UmaDbStream startStream(
            UmaDbStreamObserver<T> observer,
            Function<Res, T> mapper,
            BiConsumer<DCBGrpc.DCBStub, ServerStreamAdapter<Req, Res, T>> invoker
    ) {
        var unusable = notUsableReason();
        if (unusable != null) {
            observer.onError(unusable);
            return InactiveStream.INSTANCE;
        }

        var callContext = Context.current().withCancellation();
        var adapter = new ServerStreamAdapter<Req, Res, T>(observer, mapper, callContext, liveStreams);
        liveStreams.add(adapter);

        // Binds the call to the cancellable context, so cancelling reaches the wire.
        callContext.run(() -> invoker.accept(asyncStub, adapter));

        return adapter;
    }

    private static void requireObserver(UmaDbStreamObserver<?> observer) {
        if (observer == null) {
            throw new IllegalArgumentException("observer must not be null");
        }
    }

    @Override
    public void shutdown() {
        connection.shutdown(this::cancelLiveStreams);
    }

    /**
     * Cancels every stream still running, so that the channel has no in-flight calls
     * holding it open. Without this a live subscription, which never ends on its own,
     * would stall the graceful shutdown for its full timeout.
     */
    private void cancelLiveStreams() {
        for (var stream : Set.copyOf(liveStreams)) {
            stream.cancel();
        }
        liveStreams.clear();
    }

    /**
     * Returned when a stream is requested on a client that cannot serve it; the failure
     * has already been delivered to the observer.
     */
    private static final class InactiveStream implements UmaDbStream {

        private static final InactiveStream INSTANCE = new InactiveStream();

        @Override
        public void request(long count) {
            // no-op: the stream never started
        }

        @Override
        public void cancel() {
            // no-op: the stream never started
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
