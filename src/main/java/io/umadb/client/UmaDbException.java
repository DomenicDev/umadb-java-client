package io.umadb.client;

public sealed class UmaDbException extends RuntimeException {

    public UmaDbException(Exception e) {
        super(e);
    }

    public UmaDbException(String message) {
        super(message);
    }

    public UmaDbException() {
    }

    public static final class IntegrityException extends UmaDbException {

        public IntegrityException(String message) {
            super(message);
        }

    }

    public static final class InternalException extends UmaDbException {
        public InternalException(String description) {
            super(description);
        }
    }

    public static final class AuthenticationException extends UmaDbException {}

}
