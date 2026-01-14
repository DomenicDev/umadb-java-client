package io.umadb.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import umadb.v1.DCBGrpc;
import umadb.v1.Umadb;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;

public class UmaDbClient {

    private static final int TIMEOUT_TERMINATION_SECONDS = 5;

    private final ManagedChannel channel;
    private final DCBGrpc.DCBBlockingStub blockingStub;
    private final DCBGrpc.DCBStub asyncStub;

    public UmaDbClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        this.blockingStub = DCBGrpc.newBlockingStub(channel);
        this.asyncStub = DCBGrpc.newStub(channel);
    }

    public AppendResponse handle(AppendRequest appendRequest) {
        var umadbAppendRequest = UmaDbUtils.toUmadbAppendRequest(appendRequest);
        var umadbAppendResponse = blockingStub.append(umadbAppendRequest);
        return new AppendResponse(umadbAppendResponse.getPosition());
    }

//    public ??? handle(ReadRequest readRequest) {
//        var umadbReadRequest = UmaDbUtils.toUmadbReadRequest(readRequest);
//        var umadbReadResponse = blockingStub.read(umadbReadRequest);
//        return UmaDbUtils.toReadResponse(umadbReadResponse);
//    }

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
