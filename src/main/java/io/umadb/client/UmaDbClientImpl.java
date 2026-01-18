package io.umadb.client;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.*;
import umadb.v1.DCBGrpc;
import umadb.v1.Umadb;

import java.util.Iterator;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;

public final class UmaDbClientImpl implements UmaDbClient {

    private static final Metadata.Key<byte[]> DETAILS = Metadata.Key.of(
            "grpc-status-details-bin",
            Metadata.BINARY_BYTE_MARSHALLER
    );

    private static final int TIMEOUT_TERMINATION_SECONDS = 5;

    private final String host;
    private final int port;

    private boolean isConnected = false;
    private boolean isShutdown = false;

    private ManagedChannel channel;
    private DCBGrpc.DCBBlockingStub blockingStub;

    public UmaDbClientImpl(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void connect() {
        if (isConnected) {
            return;
        }

        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        this.blockingStub = DCBGrpc.newBlockingStub(channel);

        this.isConnected = true;
    }

    @Override
    public AppendResponse handle(AppendRequest appendRequest) {
        var umadbAppendRequest = UmaDbUtils.toUmadbAppendRequest(appendRequest);
        try {
            var umadbAppendResponse = blockingStub.append(umadbAppendRequest);
            return new AppendResponse(umadbAppendResponse.getPosition());
        } catch (StatusRuntimeException e) {
            throw extractErrorResponse(e)
                    .map(this::toUmaDbException)
                    .orElseGet(() -> toUmaDbException(e));
        }
    }


    private UmaDbException toUmaDbException(Umadb.ErrorResponse errorResponse) {
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

    private UmaDbException toUmaDbException(StatusRuntimeException e) {
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

    private Optional<Umadb.ErrorResponse> extractErrorResponse(StatusRuntimeException e) {
        return Optional.ofNullable(e.getTrailers())
                .flatMap(UmaDbClientImpl::extractErrorResponseFromMetadata);
    }

    private static Optional<Umadb.ErrorResponse> extractErrorResponseFromMetadata(Metadata trailers) {
        try {
            return Optional.of(Umadb.ErrorResponse.parseFrom(trailers.get(DETAILS)));
        } catch (InvalidProtocolBufferException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Iterator<ReadResponse> handle(ReadRequest readRequest) {
        var umadbReadRequest = UmaDbUtils.toUmadbReadRequest(readRequest);
        var grpcIterator = blockingStub.read(umadbReadRequest);
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return grpcIterator.hasNext();
            }

            @Override
            public ReadResponse next() {
                Umadb.ReadResponse grpcResponse = grpcIterator.next();
                return UmaDbUtils.toReadResponse(grpcResponse);
            }
        };
    }

    @Override
    public long getHeadPosition() {
        return blockingStub.head(Umadb.HeadRequest.getDefaultInstance()).getPosition();
    }

    @Override
    public void shutdown() {
        if (isShutdown) {
            return;
        }
        try {
            channel.shutdown().awaitTermination(TIMEOUT_TERMINATION_SECONDS, SECONDS);
            isShutdown = true;
        } catch (InterruptedException e) {
            throw new UmaDbException(e.getMessage(), e);
        }
    }
}
