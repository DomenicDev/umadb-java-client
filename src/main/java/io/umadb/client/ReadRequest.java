package io.umadb.client;

/**
 * Represents a request to read events from the event store.
 * <p>
 * A {@code ReadRequest} can be sent to the Read RPC to retrieve events matching a {@link Query}.
 * Optional fields allow controlling the starting position, reading direction, maximum number of events,
 * subscription mode, and batch size.
 *
 * <p>
 * Defaults:
 * <ul>
 *     <li>{@code backwards} defaults to {@code false}</li>
 *     <li>{@code subscribe} defaults to {@code false}</li>
 *     <li>{@code start}, {@code limit}, and {@code batchSize} are optional</li>
 * </ul>
 *
 * @param query      optional filter for selecting specific event types or tags; may be {@code null} to read all events
 * @param start      optional sequence number to start reading from; must be >= 0 if provided
 * @param backwards  optional flag to read in reverse order; defaults to {@code false} if null
 * @param limit      optional maximum number of events to return; must be > 0 if provided
 * @param subscribe  optional flag to keep the stream open; defaults to {@code false} if null
 * @param batchSize  optional batch size hint for streaming responses; must be > 0 if provided
 */
public record ReadRequest(
        Query query,
        Long start,
        Boolean backwards,
        Integer limit,
        Boolean subscribe,
        Integer batchSize
) {

    public ReadRequest {
        if (start != null && start < 0) {
            throw new IllegalArgumentException("start must be >= 0");
        }
        if (limit != null && limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        if (batchSize != null && batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }
    }

    /**
     * Creates a minimal {@code ReadRequest} selecting all events from the beginning.
     *
     * @return a {@code ReadRequest} with default parameters
     */
    public static ReadRequest all() {
        return new ReadRequest(null, null, false, null, false, null);
    }

    /**
     * Creates a {@code ReadRequest} for a specific query.
     *
     * @param query the query to filter events; may be {@code null} to read all events
     * @return a {@code ReadRequest} with default parameters
     */
    public static ReadRequest of(Query query) {
        return new ReadRequest(query, null, false, null, false, null);
    }

    /**
     * Returns a copy of this request with subscription enabled.
     *
     * @param batchSize optional batch size hint for streaming; may be {@code null}
     * @return a new {@code ReadRequest} with subscription enabled
     */
    public ReadRequest subscribe(Integer batchSize) {
        return new ReadRequest(query, start, backwards, limit, true, batchSize);
    }

    /**
     * Returns a copy of this request with a specified start position.
     *
     * @param start sequence number to start reading from
     * @return a new {@code ReadRequest} with the specified start
     */
    public ReadRequest withStart(long start) {
        return new ReadRequest(query, start, backwards, limit, subscribe, batchSize);
    }

    /**
     * Returns a copy of this request with a specified read direction.
     *
     * @param backwards true to read in reverse order
     * @return a new {@code ReadRequest} with the specified direction
     */
    public ReadRequest withDirection(boolean backwards) {
        return new ReadRequest(query, start, backwards, limit, subscribe, batchSize);
    }

    /**
     * Returns a copy of this request with a specified limit.
     *
     * @param limit maximum number of events to return
     * @return a new {@code ReadRequest} with the specified limit
     */
    public ReadRequest withLimit(int limit) {
        return new ReadRequest(query, start, backwards, limit, subscribe, batchSize);
    }

}
