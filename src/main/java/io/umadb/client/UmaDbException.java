package io.umadb.client;

/**
 * Base exception class for all exceptions thrown by the UmaDb client.
 * <p>
 * All specific exceptions extend this class, allowing clients to catch
 * either specific conditions or all UmaDb-related errors.
 */
public sealed class UmaDbException extends RuntimeException {

    public UmaDbException(String message, Exception e) {
        super(message, e);
    }

    public UmaDbException(String message) {
        super(message);
    }

    /**
     * Indicates an I/O-related error, such as network failures
     * or inability to reach the UmaDb server.
     */
    public static final class IoException extends UmaDbException {
        public IoException(String message) {
            super(message);
        }
    }

    /**
     * Indicates a failure during serialization or deserialization
     * of events, queries, or responses.
     */
    public static final class SerializationException extends UmaDbException {
        public SerializationException(String message) {
            super(message);
        }
    }

    /**
     * Indicates an integrity violation, such as appending events
     * that violate constraints or failing conditional operations.
     */
    public static final class IntegrityException extends UmaDbException {
        public IntegrityException(String message) {
            super(message);
        }
    }

    /**
     * Indicates corruption detected in persisted data,
     * such as invalid event format or inconsistent state.
     */
    public static final class CorruptionException extends UmaDbException {
        public CorruptionException(String message) {
            super(message);
        }
    }

    /**
     * Represents an internal server or client error
     * that does not fall into other specific categories.
     */
    public static final class InternalException extends UmaDbException {
        public InternalException(String description) {
            super(description);
        }
    }

    /**
     * Indicates an authentication failure, such as invalid credentials
     * or lack of authorization to access the requested resource.
     */
    public static final class AuthenticationException extends UmaDbException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
