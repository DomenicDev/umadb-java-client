package io.umadb.client;

public sealed class UmaDbException extends RuntimeException {

    public UmaDbException(String message, Exception e) {
        super(message, e);
    }

    public UmaDbException(Exception e) {
        super(e);
    }

    public UmaDbException(String message) {
        super(message);
    }

    public UmaDbException() {
    }



    public static final class IoException extends UmaDbException {
        public IoException(String message) {
            super(message);
        }
    }

    public static final class SerializationException extends UmaDbException {
        public SerializationException(String message) {
            super(message);
        }
    }

    public static final class IntegrityException extends UmaDbException {

        public IntegrityException(String message) {
            super(message);
        }

    }

    public static final class CorruptionException extends UmaDbException {

        public CorruptionException(String message) {
            super(message);
        }

    }

    public static final class InternalException extends UmaDbException {
        public InternalException(String description) {
            super(description);
        }

    }
    public static final class AuthenticationException extends UmaDbException {

        public AuthenticationException(String message) {
            super(message);
        }

    }


}
