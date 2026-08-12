package io.umadb.client;

import java.util.Iterator;
import java.util.Optional;

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
     * the head position of the event stream at the time of the response. The iterator
     * is exhausted once the head position captured at request time has been reached;
     * to keep receiving events as they are appended, use
     * {@link #subscribe(SubscribeRequest)} instead.
     *
     * @param readRequest the request describing which events to read
     * @return an iterator over {@link ReadResponse} batches
     * @throws UmaDbException if the read fails (e.g., network error or serialization failure)
     */
    Iterator<ReadResponse> handle(ReadRequest readRequest);

    /**
     * Opens a continuous subscription, returning an iterator over
     * {@link SubscribeResponse} batches.
     * <p>
     * The subscription first replays already-recorded events matching the request
     * and then blocks, yielding further batches as new events are appended. The
     * iterator does not terminate on its own; call {@link #shutdown()} to end the
     * stream.
     *
     * @param subscribeRequest the request describing which events to subscribe to
     * @return an iterator over {@link SubscribeResponse} batches
     * @throws UmaDbException if the subscription fails (e.g., network error)
     */
    Iterator<SubscribeResponse> subscribe(SubscribeRequest subscribeRequest);

    /**
     * Returns the last recorded position for a named source.
     * <p>
     * Sources are checkpointed by attaching {@link TrackingInfo} to an
     * {@link AppendRequest}.
     *
     * @param source the unique identifier of the consumer or projection
     * @return the position the source has processed up to, or
     *         {@link Optional#empty()} if no tracking info exists for it
     * @throws UmaDbException if the lookup fails
     */
    Optional<Long> getTrackingInfo(String source);

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
