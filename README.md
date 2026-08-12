# UmaDB Java Client

A lightweight Java client for interacting with the UmaDB event store via gRPC, supporting event appends, queries, and live event streaming.

> **Requires UmaDB 0.7.5 or newer.** This release targets the `umadb.v1` proto as of UmaDB 0.7.5 and is not compatible with older servers.

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

---

## Planned for Future Versions

- Async client
