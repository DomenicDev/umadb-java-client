package io.umadb.client.grpc;

import io.grpc.*;

public final class ApiKeyInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTH_HEADER =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final String apiKey;

    public ApiKeyInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next
    ) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)
        ) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(AUTH_HEADER, "Bearer " + apiKey);
                super.start(responseListener, headers);
            }
        };
    }
}
