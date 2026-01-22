package io.umadb.client;

import io.umadb.client.grpc.UmaDbClientImpl;

/**
 * Builder for creating {@link UmaDbClient} instances.
 * <p>
 * This builder supports configuring:
 * <ul>
 *   <li>Target host and port</li>
 *   <li>TLS using a custom Certificate Authority (CA)</li>
 *   <li>API key authentication (sent as a Bearer token)</li>
 * </ul>
 *
 * <h2>Security model</h2>
 * <p>
 * If an API key is configured, TLS <strong>must</strong> also be enabled.
 * The client will refuse to send API keys over non-TLS connections.
 * </p>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * UmaDbClient client = UmaDbClient.builder()
 *     .withHostAndPort("localhost", 50051)
 *     .withTls("/path/to/ca.pem")
 *     .withApiKey("my-api-key")
 *     .build();
 * }</pre>
 *
 * <p>
 * This class is mutable and not thread-safe. It is intended for one-time
 * configuration during client construction.
 * </p>
 */
public final class UmaDbClientBuilder {

    private String host;
    private int port = -1;
    private String caFilePath;
    private String apiKey;

    /**
     * Sets both the host and port for the UmaDB server.
     *
     * @param host the server hostname or IP address
     * @param port the server port
     * @return this builder instance
     */
    public UmaDbClientBuilder withHostAndPort(String host, int port) {
        this.host = host;
        this.port = port;
        return this;
    }

    /**
     * Sets the host for the UmaDB server.
     *
     * @param host the server hostname or IP address
     * @return this builder instance
     */
    public UmaDbClientBuilder withHost(String host) {
        this.host = host;
        return this;
    }

    /**
     * Sets the port for the UmaDB server.
     *
     * @param port the server port
     * @return this builder instance
     */
    public UmaDbClientBuilder withPort(int port) {
        this.port = port;
        return this;
    }

    /**
     * Enables TLS using a custom Certificate Authority (CA) certificate.
     * <p>
     * This is typically required when connecting to servers using
     * self-signed certificates or private PKI setups.
     * </p>
     *
     * @param caFilePath path to the CA certificate file (PEM format)
     * @return this builder instance
     */
    public UmaDbClientBuilder withTls(String caFilePath) {
        this.caFilePath = caFilePath;
        return this;
    }

    /**
     * Configures API key authentication.
     * <p>
     * The API key will be sent as an {@code Authorization: Bearer <token>}
     * header on all gRPC requests.
     * </p>
     *
     * <p>
     * <strong>Note:</strong> TLS must be enabled when using an API key.
     * </p>
     *
     * @param apiKey the API key to use for authentication
     * @return this builder instance
     */
    public UmaDbClientBuilder withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Enables both TLS and API key authentication in a single call.
     *
     * @param caFilePath path to the CA certificate file (PEM format)
     * @param apiKey the API key to use for authentication
     * @return this builder instance
     */
    public UmaDbClientBuilder withTlsAndApiKey(String caFilePath, String apiKey) {
        this.caFilePath = caFilePath;
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Builds a new {@link UmaDbClient} using the configured settings.
     *
     * @return a fully configured {@link UmaDbClient}
     * @throws IllegalStateException if required configuration is missing
     *                               or if an API key is configured without TLS
     */
    public UmaDbClient build() {
        return new UmaDbClientImpl(
                host,
                port,
                caFilePath,
                apiKey
        );
    }
}
