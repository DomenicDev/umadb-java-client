package io.umadb.client.grpc;

import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * Validated connection parameters shared by the blocking and asynchronous clients.
 * <p>
 * Validation happens here, at construction time, so that misconfiguration is reported
 * when the client is built rather than when it first connects.
 *
 * @param host       UmaDB server host
 * @param port       UmaDB server port
 * @param tlsEnabled whether an encrypted connection (TLS) is to be used
 * @param caFilePath optional path to a CA certificate for TLS; may be {@code null}
 * @param apiKey     optional API key, which requires TLS; may be {@code null}
 * @param executor   optional executor for gRPC callbacks; may be {@code null} to use
 *                   the gRPC default
 */
record ConnectionSettings(
        String host,
        int port,
        boolean tlsEnabled,
        String caFilePath,
        String apiKey,
        Executor executor
) {

    /**
     * @throws IllegalArgumentException if arguments are invalid or insecure
     */
    ConnectionSettings {
        if (host == null) {
            throw new IllegalArgumentException("host must not be null");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("port must be strictly positive");
        }

        // Enforce security: API keys must never be sent over plaintext channels
        if (apiKey != null && !tlsEnabled) {
            throw new IllegalArgumentException("TLS must be enabled when using API key");
        }

        if (!tlsEnabled && caFilePath != null) {
            throw new IllegalArgumentException("TLS must be enabled when using custom CA");
        }
    }

    /**
     * Returns the CA certificate location, or {@code null} when none is configured.
     */
    Path caPath() {
        return caFilePath == null ? null : Path.of(caFilePath);
    }
}
