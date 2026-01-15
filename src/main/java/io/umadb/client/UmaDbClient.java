package io.umadb.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import umadb.v1.DCBGrpc;
import umadb.v1.Umadb;

import java.util.Iterator;

import static java.util.concurrent.TimeUnit.SECONDS;

public class UmaDbClient {

    private static final int TIMEOUT_TERMINATION_SECONDS = 5;

    private final ManagedChannel channel;
    private final DCBGrpc.DCBBlockingStub blockingStub;

    public UmaDbClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        this.blockingStub = DCBGrpc.newBlockingStub(channel);
    }

    public AppendResponse handle(AppendRequest appendRequest) {
        var umadbAppendRequest = UmaDbUtils.toUmadbAppendRequest(appendRequest);
        try {
            var umadbAppendResponse = blockingStub.append(umadbAppendRequest);
            return new AppendResponse(umadbAppendResponse.getPosition());
        } catch (StatusRuntimeException e) {
            var description = e.getStatus().getDescription();
            switch (e.getStatus().getCode()) {
                case FAILED_PRECONDITION -> throw new UmaDbException.IntegrityException(description);
                case INTERNAL -> throw new UmaDbException.InternalException(description);
                case UNAUTHENTICATED -> throw new UmaDbException.AuthenticationException();
                default -> throw new UmaDbException(e);

            }
        }

    }

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

    public long getHeadPosition() {
        return blockingStub.head(Umadb.HeadRequest.getDefaultInstance()).getPosition();
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(TIMEOUT_TERMINATION_SECONDS, SECONDS);
    }
}
