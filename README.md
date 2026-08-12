# UmaDB Java Client

A lightweight Java client for interacting with the [UmaDB event store](https://umadb.io/) via gRPC, supporting event appends, queries, and live event streaming.

> **Requires UmaDB 0.7.5 or newer.**

## Installation

Add the following dependency to either the `build.gradle` or `pom.xml` file in your project.

### Gradle

```gradle
implementation("io.github.domenicdev:umadb-java-client:0.5")
```

### Apache Maven

```xml
<dependency>
    <groupId>io.github.domenicdev</groupId>
    <artifactId>umadb-java-client</artifactId>
    <version>0.5</version>
</dependency>
```


## Getting Started

### Basic Usage Example

```java
import io.umadb.client.*;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

public final class UmaDbExample {

    public static void main(String[] args) {
        // ---------------------------------------------------------------------
        // 1. Create and connect the client
        // ---------------------------------------------------------------------
        UmaDbClient client = UmaDbClient.builder()
                .withHost("localhost")
                .withPort(50051)
                .build();

        client.connect();

        try {
            // -----------------------------------------------------------------
            // 2. Append an event
            // -----------------------------------------------------------------
            Event event = Event.of(
                    "user-created",
                    List.of("users", "important"),
                    "Hello UmaDB!".getBytes(StandardCharsets.UTF_8)
            );

            AppendRequest appendRequest = new AppendRequest(
                    List.of(event),
                    null // no append condition
            );

            AppendResponse appendResponse = client.handle(appendRequest);
            System.out.println("Event appended at position: " + appendResponse.position());

            // -----------------------------------------------------------------
            // 3. Read events
            // -----------------------------------------------------------------
            ReadRequest readRequest = new ReadRequest(
                    null,   // no query (read all events)
                    0L,     // start from the beginning
                    false,  // forwards
                    10,     // limit
                    null    // default batch size
            );

            Iterator<ReadResponse> readIterator = client.handle(readRequest);

            while (readIterator.hasNext()) {
                ReadResponse response = readIterator.next();
                response.events().forEach(sequencedEvent -> {
                    System.out.println(
                            "Read event at position "
                                    + sequencedEvent.position()
                                    + " of type "
                                    + sequencedEvent.event().type()
                    );
                });
            }

            // -----------------------------------------------------------------
            // 4. Subscribe to new events (streaming)
            // -----------------------------------------------------------------
            long startPosition = client.getHeadPosition();

            SubscribeRequest subscribeRequest = SubscribeRequest
                    .all()               // no query filter
                    .after(startPosition); // resume from current head

            Iterator<SubscribeResponse> subscription = client.subscribe(subscribeRequest);

            System.out.println("Subscribed to new events...");
            while (subscription.hasNext()) {
                SubscribeResponse response = subscription.next();
                response.events().forEach(sequencedEvent -> {
                    System.out.println(
                            "Received new event at position "
                                    + sequencedEvent.position()
                                    + " of type "
                                    + sequencedEvent.event().type()
                    );
                });
            }

        } finally {
            // -----------------------------------------------------------------
            // 5. Shutdown
            // -----------------------------------------------------------------
            client.shutdown();
        }
    }
}
```

### Using TLS and API Key 

To use a secured communication over TLS, simply enable TLS when building the UmaDbClient:

```java
UmaDbClient client = UmaDbClient.builder()
        .withHost("localhost")
        .withPort(50051)
        .withTlsEnabled()
        .build();

client.connect();
```

You can also specify your own certificate authority like this (TLS will be automatically enabled): 

```java
UmaDbClient client = UmaDbClient.builder()
        .withHost("localhost")
        .withPort(50051)
        .withCertificateAuthority("server.pem")
        .build();
```

For API key-protected servers, use the `withApiKey` when building the client:  

```java
UmaDbClient client = UmaDbClient.builder()
        .withHost("localhost")
        .withPort(50051)
        .withApiKey("umadb:example-api-key-123456789")
        .build();
```

To specify both CA + API key, simply use the corresponding builder methods:

```java
UmaDbClient client = UmaDbClient.builder()
        .withHost("localhost")
        .withPort(50051)
        .withCertificateAuthority("server.pem")
        .withApiKey("umadb:example-api-key-123456789")
        .build();
```

### Conditional append (optimistic concurrency)

```java
QueryItem boundary = QueryItem.of(
        List.of("order-created"),
        List.of("order-123")
);

Query query = Query.of(boundary);

long lastKnownPosition = client.getHeadPosition();

AppendCondition condition = AppendCondition
        .failIfExists(query)
        .after(lastKnownPosition);

AppendRequest request = new AppendRequest(
        List.of(
                Event.of(
                        "order-created",
                        List.of("order-123"),
                        "Order created".getBytes(StandardCharsets.UTF_8)
                )
        ),
        condition
);

client.handle(request);
```

If a matching event already exists after the given position, the append will fail with:

```java
UmaDbException.IntegrityException
```

### Event metadata

Events can carry metadata — contextual key/value pairs such as correlation IDs or user
agents that are not part of the payload itself:

```java
Event event = Event.of(
                "order-created",
                List.of("order-123"),
                "Order created".getBytes(StandardCharsets.UTF_8)
        )
        .withMetadata("correlation-id", "b7f1c3e4")
        .withMetadata("user-agent", "checkout-service/2.1");

client.handle(AppendRequest.of(List.of(event)));
```

Metadata is returned with every event that is read or streamed back:

```java
Map<String, String> metadata = sequencedEvent.event().metadata();
```

Events created without metadata expose an empty map, never `null`.

### Tracking consumer progress

A consumer can checkpoint how far it has processed by attaching `TrackingInfo` to an
append. The cursor advances atomically with the append, so the checkpoint can never
drift from the events it describes:

```java
AppendRequest request = AppendRequest
        .of(List.of(event))
        .withTrackingInfo(TrackingInfo.of("order-projection", lastProcessedPosition));

client.handle(request);
```

The saved position can be read back when the consumer restarts, so it can resume from
where it left off:

```java
long resumeFrom = client.getTrackingInfo("order-projection").orElse(0L);

Iterator<SubscribeResponse> subscription =
        client.subscribe(SubscribeRequest.all().after(resumeFrom));
```

`getTrackingInfo` returns `Optional.empty()` for a source that has never been
checkpointed.

---

## Asynchronous client

`UmaDbAsyncClient` is the non-blocking counterpart to `UmaDbClient`. It is built from the
same builder, and owns its own connection:

```java
UmaDbAsyncClient client = UmaDbClient.builder()
        .withHost("localhost")
        .withPort(50051)
        .buildAsync();

client.connect();
```

Unary operations return a `CompletableFuture`:

```java
CompletableFuture<AppendResponse> appended = client.handle(
        AppendRequest.of(List.of(event))
);

appended.thenAccept(response ->
        System.out.println("Appended at " + response.position())
);

CompletableFuture<Long> head = client.getHeadPosition();
CompletableFuture<Optional<Long>> cursor = client.getTrackingInfo("order-projection");
```

Reads and subscriptions deliver their batches to a `UmaDbStreamObserver` and hand back a
`UmaDbStream` for cancellation — no dedicated consumer thread required:

```java
UmaDbStream subscription = client.subscribe(
        SubscribeRequest.all().after(client.getHeadPosition().join()),
        new UmaDbStreamObserver<SubscribeResponse>() {

            @Override
            public void onNext(SubscribeResponse response) {
                response.events().forEach(e ->
                        System.out.println("Received " + e.event().type() + " at " + e.position())
                );
            }

            @Override
            public void onError(UmaDbException error) {
                error.printStackTrace();
            }

            @Override
            public void onCompleted() {
                System.out.println("Stream ended");
            }
        }
);

// later, from any thread
subscription.cancel();
```

The client implements `AutoCloseable`, so it can also be used in a try-with-resources block.

### Errors arrive wrapped

This is the one behavioural difference worth internalising. The blocking client throws
`UmaDbException` directly; a future instead completes *exceptionally*, so the exception you
catch is a `CompletionException` or `ExecutionException` and the `UmaDbException` is its
**cause**:

```java
try {
    client.handle(request).get();
} catch (ExecutionException e) {
    if (e.getCause() instanceof UmaDbException.IntegrityException conflict) {
        // handle the conditional-append conflict
    }
}
```

Streaming failures do not have this problem: `onError` receives the `UmaDbException`
directly.

Argument validation is the exception to the rule — passing a null observer or a blank
source throws immediately, because that is a programming error rather than a remote
failure.

### Threading

Callbacks and future completions run on a gRPC callback thread. **Do not block in them**,
and do not do long-running work there: hand off to your own executor, or use
`thenApplyAsync(fn, myExecutor)` rather than `thenApply(fn)`. Blocking on another UmaDB
call from inside a callback risks deadlock if the callback pool is saturated.

You can supply the pool gRPC dispatches on:

```java
UmaDbAsyncClient client = UmaDbClient.builder()
        .withHostAndPort("localhost", 50051)
        .withExecutor(Executors.newVirtualThreadPerTaskExecutor())
        .buildAsync();
```

Callbacks for any single stream stay serialized regardless of the executor, so event
ordering is always preserved.

### Flow control

By default a stream requests one batch at a time and asks for the next only once your
`onNext` returns, so nothing is buffered and a slow consumer throttles the server. That
default matters for subscriptions, which never end on their own.

For explicit control, request demand from `onStart` — the stream then delivers only what
you ask for:

```java
new UmaDbStreamObserver<ReadResponse>() {

    @Override
    public void onStart(UmaDbStream stream) {
        stream.request(1);        // pull mode: nothing arrives unrequested
    }

    @Override
    public void onNext(ReadResponse response) {
        process(response);
        stream.request(1);        // ask for the next one when ready
    }

    // onError / onCompleted ...
}
```

Events held in memory are roughly `outstanding demand × batch size`, where batch size is
the one set on the `ReadRequest` or `SubscribeRequest`.

---

## Migrating from 0.5

This release targets the UmaDB 0.7.5 proto and contains breaking changes.

**`ReadRequest` no longer has a `subscribe` flag.** Reads now always terminate at the
head position captured when the request was received. Live streaming moved to a
dedicated RPC:

```java
// Before
ReadRequest request = new ReadRequest(query, position, false, null, true, null);
Iterator<ReadResponse> stream = client.handle(request);

// After
SubscribeRequest request = SubscribeRequest.of(query).after(position);
Iterator<SubscribeResponse> stream = client.subscribe(request);
```

Note that `ReadRequest`'s canonical constructor lost a component and now takes five
arguments, and `ReadRequest.subscribe(Integer)` has been removed.

**`UmaDbException.InvalidArgumentException` is new.** Malformed requests previously
surfaced as `SerializationException`; they now map to the more accurate
`InvalidArgumentException`. Code catching `SerializationException` for this case needs
updating.

**`Event`, `SequencedEvent`, and `AppendRequest` gained components** (`metadata` and
`trackingInfo`). Their previous constructor arities still work and default the new
fields, so existing call sites keep compiling.

**`shutdown()` now cancels streams you have stopped consuming.** Previously an abandoned
read or subscription kept the channel alive for the full 15-second grace period and left
its transport threads behind; shutdown now cancels live calls first and forces termination
if the graceful path does not finish. If a thread is still iterating such a stream when you
shut the client down, it now sees a `UmaDbException` instead of blocking.
