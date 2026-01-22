package io.umadb.client;

import java.util.Iterator;

/**
 * Main interface for interacting with the UmaDb event store.
 * <p>
 * Clients use this interface to append events, read events, and manage
 * the connection to the UmaDb server. Implementations handle all
 * network communication, serialization, and concurrency internally.
 */
public interface UmaDbClient {

    /**
     * Creates a new {@link UmaDbClientBuilder} for constructing an
     * {@link UmaDbClient} instance.
     * <p>
     * The builder allows configuring:
     * <ul>
     *   <li>Server host and port</li>
     *   <li>TLS using a custom Certificate Authority (CA)</li>
     *   <li>API key authentication</li>
     * </ul>
     *
     * <h2>Example</h2>
     * <pre>{@code
     * UmaDbClient client = UmaDbClient.builder()
     *     .withHostAndPort("localhost", 50051)
     *     .withTls("/path/to/ca.pem")
     *     .withApiKey("my-api-key")
     *     .build();
     * }</pre>
     *
     * @return a new {@link UmaDbClientBuilder}
     */
    static UmaDbClientBuilder builder() {
        return new UmaDbClientBuilder();
    }

    /**
     * Establishes a connection to the UmaDb server.
     * <p>
     * This method must be called before performing any operations
     * such as appending or reading events.
     *
     * @throws UmaDbException if the connection cannot be established
     */
    void connect();

    /**
     * Handles an append request, writing events to the event store.
     * <p>
     * The {@link AppendRequest} may include optional {@link AppendCondition}s
     * that enforce conditional appends. Returns an {@link AppendResponse}
     * containing the position of the last appended event.
     *
     * @param appendRequest the request describing the events to append
     * @return the response containing the position of the last appended event
     * @throws UmaDbException if the append fails (e.g., due to conditional constraints,
     *                        serialization errors, or server issues)
     */
    AppendResponse handle(AppendRequest appendRequest);

    /**
     * Handles a read request, returning an iterator over {@link ReadResponse} objects.
     * <p>
     * Each {@link ReadResponse} contains a batch of sequenced events and optionally
     * the head position of the event stream at the time of the response. If
     * {@link ReadRequest#subscribe()} is {@code true}, the iterator will continue
     * to provide new events as they are appended.
     *
     * @param readRequest the request describing which events to read
     * @return an iterator over {@link ReadResponse} batches
     * @throws UmaDbException if the read fails (e.g., network error or serialization failure)
     */
    Iterator<ReadResponse> handle(ReadRequest readRequest);

    /**
     * Returns the position of the most recent event in the event store.
     * <p>
     * This value can be used for optimistic concurrency control, checkpointing,
     * or as a reference point for subsequent reads.
     *
     * @return the sequence number of the latest event
     * @throws UmaDbException.IoException if the position cannot be retrieved
     */
    long getHeadPosition();

    /**
     * Shuts down the client, closing any active connections and releasing resources.
     * <p>
     * After calling this method, the client should not be used for any further operations.
     *
     * @throws UmaDbException if the connection cannot be closed properly
     */
    void shutdown();
}
