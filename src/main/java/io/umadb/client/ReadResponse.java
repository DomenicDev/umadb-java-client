package io.umadb.client;

import java.util.List;

/**
 * Represents the response returned from a read operation.
 * <p>
 * Contains a list of sequenced events and optionally the head position of the event stream
 * at the time the response was generated.
 *
 * <p>
 * If {@code head} is provided, it can be used as a reference point for subsequent reads
 * or for subscribing to new events.
 *
 * @param events the list of sequenced events returned by the read
 * @param head   the position of the latest event in the store when the response was generated;
 *               may be {@code null} if unknown
 */
public record ReadResponse(
        List<SequencedEvent> events,
        Long head
) {
}
