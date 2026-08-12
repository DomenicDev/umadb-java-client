package io.umadb.client.grpc;

import io.grpc.ChannelCredentials;
import io.grpc.ClientInterceptor;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link ManagedChannel}s from {@link ConnectionSettings}.
 * <p>
 * Package-private internal machinery shared by the blocking and asynchronous clients.
 */
final class ChannelFactory {

    private ChannelFactory() {
        // utility class
    }

    /**
     * Creates a new managed channel configured with the given settings.
     *
     * @param settings the validated connection settings
     * @return a new, not-yet-connected managed channel
     * @throws IOException if TLS credentials cannot be loaded
     */
    static ManagedChannel newChannel(ConnectionSettings settings) throws IOException {
        var channelBuilder = Grpc
                .newChannelBuilderForAddress(
                        settings.host(),
                        settings.port(),
                        resolveChannelCredentials(settings)
                )
                .intercept(resolveClientInterceptors(settings));

        if (settings.executor() != null) {
            channelBuilder.executor(settings.executor());
        }

        return channelBuilder.build();
    }

    /**
     * Returns the list of gRPC client interceptors to apply.
     * <p>
     * Currently only used for API key authentication.
     */
    private static List<ClientInterceptor> resolveClientInterceptors(ConnectionSettings settings) {
        var interceptors = new ArrayList<ClientInterceptor>();
        if (settings.apiKey() != null) {
            interceptors.add(new ApiKeyInterceptor(settings.apiKey()));
        }
        return interceptors;
    }

    /**
     * Resolves the appropriate channel credentials (TLS or insecure).
     */
    private static ChannelCredentials resolveChannelCredentials(ConnectionSettings settings) throws IOException {
        return settings.tlsEnabled()
                ? getTlsChannelCredentials(settings.caPath())
                : getInsecureChannelCredentials();
    }

    private static ChannelCredentials getTlsChannelCredentials(Path caPath) throws IOException {
        if (caPath != null) {
            return TlsChannelCredentials.newBuilder()
                    .trustManager(caPath.toFile())
                    .build();
        } else {
            return TlsChannelCredentials.create();
        }
    }

    private static ChannelCredentials getInsecureChannelCredentials() {
        return InsecureChannelCredentials.create();
    }
}
