package io.umadb.client.grpc;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.umadb.client.UmaDbException;
import org.junit.jupiter.api.Test;
import umadb.v1.Umadb;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the mapping of gRPC failures onto the {@link UmaDbException} hierarchy.
 */
class GrpcErrorTranslatorTest {

    private static final Metadata.Key<byte[]> DETAILS = Metadata.Key.of(
            "grpc-status-details-bin",
            Metadata.BINARY_BYTE_MARSHALLER
    );

    // ----------------------
    // Helper Methods
    // ----------------------

    private static Metadata trailersWith(Umadb.ErrorResponse.ErrorType type, String message) {
        var trailers = new Metadata();
        trailers.put(DETAILS, Umadb.ErrorResponse.newBuilder()
                .setErrorType(type)
                .setMessage(message)
                .build()
                .toByteArray());
        return trailers;
    }

    private static StatusRuntimeException statusWithDetails(Umadb.ErrorResponse.ErrorType type, String message) {
        return new StatusRuntimeException(Status.UNKNOWN, trailersWith(type, message));
    }

    // ----------------------
    // Structured error details take precedence
    // ----------------------

    @Test
    void detailsTrailerMapsEveryErrorType() {
        assertInstanceOf(UmaDbException.IoException.class,
                GrpcErrorTranslator.translate(statusWithDetails(Umadb.ErrorResponse.ErrorType.IO, "io")));
        assertInstanceOf(UmaDbException.SerializationException.class,
                GrpcErrorTranslator.translate(statusWithDetails(Umadb.ErrorResponse.ErrorType.SERIALIZATION, "ser")));
        assertInstanceOf(UmaDbException.IntegrityException.class,
                GrpcErrorTranslator.translate(statusWithDetails(Umadb.ErrorResponse.ErrorType.INTEGRITY, "int")));
        assertInstanceOf(UmaDbException.CorruptionException.class,
                GrpcErrorTranslator.translate(statusWithDetails(Umadb.ErrorResponse.ErrorType.CORRUPTION, "cor")));
        assertInstanceOf(UmaDbException.InternalException.class,
                GrpcErrorTranslator.translate(statusWithDetails(Umadb.ErrorResponse.ErrorType.INTERNAL, "internal")));
        assertInstanceOf(UmaDbException.AuthenticationException.class,
                GrpcErrorTranslator.translate(statusWithDetails(Umadb.ErrorResponse.ErrorType.AUTHENTICATION, "auth")));
        assertInstanceOf(UmaDbException.InvalidArgumentException.class,
                GrpcErrorTranslator.translate(
                        statusWithDetails(Umadb.ErrorResponse.ErrorType.INVALID_ARGUMENT, "invalid")));
    }

    @Test
    void detailsTrailerPreservesTheServerMessage() {
        var translated = GrpcErrorTranslator.translate(
                statusWithDetails(Umadb.ErrorResponse.ErrorType.INTEGRITY, "condition failed")
        );

        assertEquals("condition failed", translated.getMessage());
    }

    @Test
    void detailsTrailerWinsOverTheStatusCode() {
        // Status says UNAUTHENTICATED, details say INTEGRITY: the details are authoritative
        var e = new StatusRuntimeException(
                Status.UNAUTHENTICATED,
                trailersWith(Umadb.ErrorResponse.ErrorType.INTEGRITY, "from details")
        );

        assertInstanceOf(UmaDbException.IntegrityException.class, GrpcErrorTranslator.translate(e));
    }

    // ----------------------
    // Status code fallback
    // ----------------------

    @Test
    void statusCodeIsUsedWhenNoTrailersArePresent() {
        assertInstanceOf(UmaDbException.AuthenticationException.class,
                GrpcErrorTranslator.translate(new StatusRuntimeException(Status.UNAUTHENTICATED)));
        assertInstanceOf(UmaDbException.IntegrityException.class,
                GrpcErrorTranslator.translate(new StatusRuntimeException(Status.FAILED_PRECONDITION)));
        assertInstanceOf(UmaDbException.CorruptionException.class,
                GrpcErrorTranslator.translate(new StatusRuntimeException(Status.DATA_LOSS)));
        assertInstanceOf(UmaDbException.InvalidArgumentException.class,
                GrpcErrorTranslator.translate(new StatusRuntimeException(Status.INVALID_ARGUMENT)));
        assertInstanceOf(UmaDbException.InternalException.class,
                GrpcErrorTranslator.translate(new StatusRuntimeException(Status.INTERNAL)));
    }

    @Test
    void unmappedStatusCodeFallsBackToTheBaseException() {
        var translated = GrpcErrorTranslator.translate(new StatusRuntimeException(Status.UNAVAILABLE));

        assertEquals(UmaDbException.class, translated.getClass());
        assertTrue(translated.getMessage().startsWith("gRPC error: "));
    }

    @Test
    void malformedDetailsTrailerFallsBackToTheStatusCode() {
        var trailers = new Metadata();
        trailers.put(DETAILS, new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});

        var translated = GrpcErrorTranslator.translate(
                new StatusRuntimeException(Status.UNAUTHENTICATED, trailers)
        );

        assertInstanceOf(UmaDbException.AuthenticationException.class, translated);
    }

    @Test
    void emptyTrailersFallBackToTheStatusCode() {
        var translated = GrpcErrorTranslator.translate(
                new StatusRuntimeException(Status.INTERNAL, new Metadata())
        );

        assertInstanceOf(UmaDbException.InternalException.class, translated);
    }

    // ----------------------
    // Throwable overload, used by the async callbacks
    // ----------------------

    @Test
    void throwableOverloadPassesUmaDbExceptionsThrough() {
        var original = new UmaDbException.IntegrityException("already translated");

        assertSame(original, GrpcErrorTranslator.translate((Throwable) original));
    }

    @Test
    void throwableOverloadHandlesStatusRuntimeException() {
        Throwable t = statusWithDetails(Umadb.ErrorResponse.ErrorType.CORRUPTION, "corrupt");

        assertInstanceOf(UmaDbException.CorruptionException.class, GrpcErrorTranslator.translate(t));
    }

    @Test
    void throwableOverloadHandlesStatusException() {
        Throwable t = new StatusException(Status.UNAUTHENTICATED);

        assertInstanceOf(UmaDbException.AuthenticationException.class, GrpcErrorTranslator.translate(t));
    }

    @Test
    void throwableOverloadReadsDetailsFromStatusException() {
        Throwable t = new StatusException(
                Status.UNKNOWN,
                trailersWith(Umadb.ErrorResponse.ErrorType.INVALID_ARGUMENT, "bad request")
        );

        var translated = GrpcErrorTranslator.translate(t);

        assertInstanceOf(UmaDbException.InvalidArgumentException.class, translated);
        assertEquals("bad request", translated.getMessage());
    }

    @Test
    void throwableOverloadWrapsArbitraryFailures() {
        // This is the path taken when a consumer's own onNext throws
        var translated = GrpcErrorTranslator.translate(new IllegalStateException("boom"));

        assertEquals(UmaDbException.class, translated.getClass());
        assertEquals("boom", translated.getMessage());
        assertInstanceOf(IllegalStateException.class, translated.getCause());
    }

    @Test
    void throwableOverloadHandlesNull() {
        var translated = GrpcErrorTranslator.translate((Throwable) null);

        assertEquals(UmaDbException.class, translated.getClass());
        assertEquals("Unknown error", translated.getMessage());
    }
}
