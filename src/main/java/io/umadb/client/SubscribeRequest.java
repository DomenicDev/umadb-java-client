package io.umadb.client;

/**
 * Represents a request to open a real-time, continuous stream of events.
 * <p>
 * A subscription first replays already-recorded events matching the query and
 * then continues to deliver new events as they are appended. Unlike a
 * {@link ReadRequest}, the stream does not terminate at the current head.
 *
 * @param query     optional filter selecting which events to listen to;
 *                  may be {@code null} to receive all events
 * @param after     optional position to resume from; only events strictly after
 *                  this position are delivered. May be {@code null} to start
 *                  from the beginning of the stream
 * @param batchSize optional hint for how many events to deliver per stream push;
 *                  must be &gt; 0 if provided
 */
public record SubscribeRequest(
        Query query,
        Long after,
        Integer batchSize
) {

    /**
     * Creates a new {@code SubscribeRequest}.
     *
     * @throws IllegalArgumentException if validation constraints are violated
     */
    public SubscribeRequest {
        if (after != null && after < 0) {
            throw new IllegalArgumentException("after must be >= 0");
        }
        if (batchSize != null && batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }
    }

    /**
     * Creates a subscription to all events, starting from the beginning of the stream.
     *
     * @return a {@code SubscribeRequest} with default parameters
     */
    public static SubscribeRequest all() {
        return new SubscribeRequest(null, null, null);
    }

    /**
     * Creates a subscription filtered by the given query.
     *
     * @param query the query to filter events; may be {@code null} to receive all events
     * @return a {@code SubscribeRequest} for the given query
     */
    public static SubscribeRequest of(Query query) {
        return new SubscribeRequest(query, null, null);
    }

    /**
     * Returns a copy of this request resuming strictly after the given position.
     *
     * @param after the position to resume from
     * @return a new {@code SubscribeRequest} with the specified resume point
     */
    public SubscribeRequest after(long after) {
        return new SubscribeRequest(query, after, batchSize);
    }

    /**
     * Returns a copy of this request with the given batch size hint.
     *
     * @param batchSize the maximum number of events per stream push
     * @return a new {@code SubscribeRequest} with the specified batch size
     */
    public SubscribeRequest withBatchSize(int batchSize) {
        return new SubscribeRequest(query, after, batchSize);
    }
}
