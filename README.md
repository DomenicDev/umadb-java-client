# UmaDB Java Client

A lightweight Java client for interacting with the UmaDB event store via gRPC, supporting event appends, queries, and live event streaming.

## Installation

Add the following dependency to either the `build.gradle` or `pom.xml` file in your project.

### Gradle

```gradle
implementation("io.github.domenicdev:umadb-java-client:0.1")
```

### Apache Maven

```xml
<dependency>
    <groupId>io.github.domenicdev</groupId>
    <artifactId>umadb-java-client</artifactId>
    <version>0.1</version>
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
                    false,  // do not subscribe
                    null
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

            ReadRequest subscribeRequest = new ReadRequest(
                    null,          // no query
                    startPosition, // start from current head
                    false,
                    null,          // unlimited
                    true,          // subscribe
                    null
            );

            Iterator<ReadResponse> subscription = client.handle(subscribeRequest);

            System.out.println("Subscribed to new events...");
            while (subscription.hasNext()) {
                ReadResponse response = subscription.next();
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

### Using TLS and an API key

For secured deployments (TLS + API key), the builder makes this explicit and safe:

```java
UmaDbClient client = UmaDbClient.builder()
        .withHost("localhost")
        .withPort(50051)
        .withTlsAndApiKey(
                "server.pem",                       // CA certificate
                "umadb:example-api-key-123456789"   // API key
        )
        .build();

client.connect();
```

> ⚠️ Important:
> An API key requires TLS. The client will fail fast if an API key is provided without TLS.

### Conditional append (optimistic concurrency)

```java
QueryItem boundary = QueryItem.ofTypesAndTags(
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

---

## Planned for Future Versions

- Async client
