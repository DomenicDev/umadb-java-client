package io.umadb.client.grpc;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.umadb.client.UmaDbException;
import umadb.v1.Umadb;

import java.util.Optional;

/**
 * Translates gRPC failures into the {@link UmaDbException} hierarchy.
 * <p>
 * The server may attach a structured {@link Umadb.ErrorResponse} to the
 * {@code grpc-status-details-bin} trailer. When present it is preferred, since it
 * carries UmaDB's own error classification; otherwise the generic gRPC status code
 * is mapped instead.
 * <p>
 * Package-private on purpose: this is internal machinery shared by the blocking and
 * asynchronous client implementations, and must not become part of the published API.
 */
final class GrpcErrorTranslator {

    /**
     * gRPC metadata key used to extract structured UmaDB error details
     * returned by the server.
     */
    private static final Metadata.Key<byte[]> DETAILS = Metadata.Key.of(
            "grpc-status-details-bin",
            Metadata.BINARY_BYTE_MARSHALLER
    );

    private GrpcErrorTranslator() {
        // utility class
    }

    /**
     * Translates a gRPC status exception raised by a blocking call.
     *
     * @param e the exception thrown by the stub
     * @return the corresponding {@link UmaDbException}
     */
    static UmaDbException translate(StatusRuntimeException e) {
        return extractErrorResponse(e.getTrailers())
                .map(GrpcErrorTranslator::toUmaDbException)
                .orElseGet(() -> toUmaDbException(e));
    }

    /**
     * Translates an arbitrary failure delivered to an asynchronous callback.
     * <p>
     * Asynchronous gRPC callbacks receive a bare {@link Throwable}, which may be a
     * {@link StatusRuntimeException}, a {@link StatusException}, an exception raised while
     * mapping a response, or an exception thrown by user code. All of them are funnelled
     * through here so that observers only ever see a {@link UmaDbException}.
     *
     * @param t the failure to translate
     * @return the corresponding {@link UmaDbException}
     */
    static UmaDbException translate(Throwable t) {
        return switch (t) {
            case UmaDbException umaDbException -> umaDbException;
            case StatusRuntimeException e -> translate(e);
            case StatusException e -> extractErrorResponse(e.getTrailers())
                    .map(GrpcErrorTranslator::toUmaDbException)
                    .orElseGet(() -> toUmaDbException(e.getStatus(), e.getMessage(), e));
            case null -> new UmaDbException("Unknown error");
            default -> new UmaDbException(t.getMessage(), asException(t));
        };
    }

    private static Exception asException(Throwable t) {
        return t instanceof Exception e ? e : new RuntimeException(t);
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
            case INVALID_ARGUMENT -> new UmaDbException.InvalidArgumentException(errorMessage);
            case UNRECOGNIZED -> new UmaDbException(errorMessage);
        };
    }

    private static UmaDbException toUmaDbException(StatusRuntimeException e) {
        return toUmaDbException(e.getStatus(), e.getMessage(), e);
    }

    private static UmaDbException toUmaDbException(Status status, String errorMessage, Exception cause) {
        return switch (status.getCode()) {
            case UNAUTHENTICATED -> new UmaDbException.AuthenticationException(errorMessage);
            case FAILED_PRECONDITION -> new UmaDbException.IntegrityException(errorMessage);
            case DATA_LOSS -> new UmaDbException.CorruptionException(errorMessage);
            case INVALID_ARGUMENT -> new UmaDbException.InvalidArgumentException(errorMessage);
            case INTERNAL -> new UmaDbException.InternalException(errorMessage);
            default -> new UmaDbException("gRPC error: %s".formatted(errorMessage), cause);
        };
    }

    private static Optional<Umadb.ErrorResponse> extractErrorResponse(Metadata trailers) {
        if (trailers == null) {
            return Optional.empty();
        }
        try {
            if (trailers.containsKey(DETAILS)) {
                return Optional.of(
                        Umadb.ErrorResponse.parseFrom(trailers.get(DETAILS))
                );
            }
        } catch (InvalidProtocolBufferException _) {
            // Fall back to generic gRPC error handling
        }
        return Optional.empty();
    }
}
