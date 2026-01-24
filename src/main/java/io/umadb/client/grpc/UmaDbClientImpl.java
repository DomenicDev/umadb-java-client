package io.umadb.client.grpc;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.*;
import io.umadb.client.*;
import umadb.v1.DCBGrpc;
import umadb.v1.Umadb;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static java.util.concurrent.TimeUnit.SECONDS;

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

    /**
     * gRPC metadata key used to extract structured UmaDB error details
     * returned by the server.
     */
    private static final Metadata.Key<byte[]> DETAILS = Metadata.Key.of(
            "grpc-status-details-bin",
            Metadata.BINARY_BYTE_MARSHALLER
    );

    /**
     * Maximum time to wait for a graceful channel shutdown.
     */
    private static final int TIMEOUT_TERMINATION_SECONDS = 15;

    private final String host;
    private final int port;
    private final String optionalApiKey;
    private final Path optionalCaFilePath;

    private boolean isConnected = false;
    private boolean isShutdown = false;

    private ManagedChannel channel;
    private DCBGrpc.DCBBlockingStub blockingStub;

    /**
     * Creates a new client implementation.
     *
     * @param host       UmaDB server host
     * @param port       UmaDB server port
     * @param caFilePath optional path to a CA certificate for TLS
     * @param apiKey     optional API key (requires TLS)
     * @throws IllegalArgumentException if arguments are invalid or insecure
     */
    public UmaDbClientImpl(String host, int port, String caFilePath, String apiKey) {
        if (host == null) {
            throw new IllegalArgumentException("host must not be null");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("port must be strictly positive");
        }

        // Enforce security: API keys must never be sent over plaintext channels
        if (apiKey != null && caFilePath == null) {
            throw new IllegalArgumentException("TLS cert file must be defined when using API key");
        }

        this.host = host;
        this.port = port;
        this.optionalApiKey = apiKey;
        this.optionalCaFilePath = Optional.ofNullable(caFilePath).map(Path::of).orElse(null);
    }

    @Override
    public void connect() {
        if (isConnected) {
            return;
        }

        try {
            ChannelCredentials channelCredentials = resolveChannelCredentials();
            List<ClientInterceptor> interceptors = resolveClientInterceptors();

            // Build the managed channel with TLS and interceptors (if any)
            this.channel = Grpc
                    .newChannelBuilderForAddress(host, port, channelCredentials)
                    .intercept(interceptors)
                    .build();

            this.blockingStub = DCBGrpc.newBlockingStub(channel);

            this.isConnected = true;
        } catch (Exception e) {
            throw new UmaDbException.IoException(
                    "Failed to connect to UmaDB: " + e.getMessage()
            );
        }
    }

    /**
     * Returns the list of gRPC client interceptors to apply.
     * <p>
     * Currently only used for API key authentication.
     */
    private List<ClientInterceptor> resolveClientInterceptors() {
        var interceptors = new ArrayList<ClientInterceptor>();
        if (optionalApiKey != null) {
            interceptors.add(new ApiKeyInterceptor(optionalApiKey));
        }
        return interceptors;
    }

    /**
     * Resolves the appropriate channel credentials (TLS or insecure).
     */
    private ChannelCredentials resolveChannelCredentials() throws IOException {
        return isTlsEnabled() ?
                getTlsChannelCredentials() :
                InsecureChannelCredentials.create();
    }

    private ChannelCredentials getTlsChannelCredentials() throws IOException {
        return TlsChannelCredentials.newBuilder()
                .trustManager(optionalCaFilePath.toFile())
                .build();
    }

    @Override
    public AppendResponse handle(AppendRequest appendRequest) {
        var umadbAppendRequest = UmaDbUtils.toUmadbAppendRequest(appendRequest);
        try {
            var umadbAppendResponse = blockingStub.append(umadbAppendRequest);
            return new AppendResponse(umadbAppendResponse.getPosition());
        } catch (StatusRuntimeException e) {
            throw resolveUmaDbException(e);
        }
    }

    private static UmaDbException resolveUmaDbException(StatusRuntimeException e) {
        return extractErrorResponse(e)
                .map(UmaDbClientImpl::toUmaDbException)
                .orElseGet(() -> toUmaDbException(e));
    }

    private static UmaDbException toUmaDbException(Umadb.ErrorResponse errorResponse) {
        var errorMessage = errorResponse.getMessage();
        return switch (errorResponse.getErrorType()) {
            case IO -> new UmaDbException.IoException(errorMessage);
            case SERIALIZATION -> new UmaDbException.SerializationException(errorMessage);
            case INTEGRITY -> new UmaDbException.IntegrityException(errorMessage);
            case CORRUPTION -> new UmaDbException.CorruptionException(errorMessage);
            case INTERNAL -> new UmaDbException.InternalException(errorMessage);
            case AUTHENTICATION -> new UmaDbException.AuthenticationException(errorMessage);
            case UNRECOGNIZED -> new UmaDbException(errorMessage);
        };
    }

    private static UmaDbException toUmaDbException(StatusRuntimeException e) {
        var errorMessage = e.getMessage();
        return switch (e.getStatus().getCode()) {
            case UNAUTHENTICATED -> new UmaDbException.AuthenticationException(errorMessage);
            case FAILED_PRECONDITION -> new UmaDbException.IntegrityException(errorMessage);
            case DATA_LOSS -> new UmaDbException.CorruptionException(errorMessage);
            case INVALID_ARGUMENT -> new UmaDbException.SerializationException(errorMessage);
            case INTERNAL -> new UmaDbException.InternalException(errorMessage);
            default -> new UmaDbException("gRPC error: %s".formatted(errorMessage), e);
        };
    }

    private static Optional<Umadb.ErrorResponse> extractErrorResponse(StatusRuntimeException e) {
        return Optional.ofNullable(e.getTrailers())
                .flatMap(UmaDbClientImpl::extractErrorResponseFromMetadata);
    }

    private static Optional<Umadb.ErrorResponse> extractErrorResponseFromMetadata(Metadata trailers) {
        try {
            if (trailers.containsKey(DETAILS)) {
                return Optional.of(
                        Umadb.ErrorResponse.parseFrom(trailers.get(DETAILS))
                );
            }
        } catch (InvalidProtocolBufferException ignored) {
            // Fall back to generic gRPC error handling
        }
        return Optional.empty();
    }

    private boolean isTlsEnabled() {
        return this.optionalCaFilePath != null;
    }

    @Override
    public Iterator<ReadResponse> handle(ReadRequest readRequest) {
        var umadbReadRequest = UmaDbUtils.toUmadbReadRequest(readRequest);
        try {
            var grpcIterator = blockingStub.read(umadbReadRequest);
            return new ReadResponseIterator(grpcIterator);
        } catch (StatusRuntimeException e) {
            throw resolveUmaDbException(e);
        }
    }

    @Override
    public long getHeadPosition() {
        try {
            return blockingStub.head(Umadb.HeadRequest.getDefaultInstance()).getPosition();
        } catch (StatusRuntimeException e) {
            throw resolveUmaDbException(e);
        }
    }

    @Override
    public void shutdown() {
        if (isShutdown || !isConnected) {
            return;
        }
        try {
            channel.shutdown().awaitTermination(TIMEOUT_TERMINATION_SECONDS, SECONDS);
            isShutdown = true;
        } catch (InterruptedException e) {
            throw new UmaDbException(e.getMessage(), e);
        }
    }

    private record ReadResponseIterator(Iterator<Umadb.ReadResponse> grpcIterator) implements Iterator<ReadResponse> {

        @Override
        public boolean hasNext() {
            try {
                return grpcIterator.hasNext();
            } catch (StatusRuntimeException e) {
                throw resolveUmaDbException(e);
            }
        }

        @Override
        public ReadResponse next() {
            try {
                Umadb.ReadResponse grpcResponse = grpcIterator.next();
                return UmaDbUtils.toReadResponse(grpcResponse);
            } catch (StatusRuntimeException e) {
                throw resolveUmaDbException(e);
            }
        }
    }
}
