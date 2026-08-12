package io.umadb.client;

import java.util.List;

/**
 * A chunk of events pushed to a subscriber.
 * <p>
 * Unlike {@link ReadResponse}, a subscription chunk carries no head position:
 * the stream has no defined end while the subscription is open.
 *
 * @param events the sequenced events delivered in this chunk
 */
public record SubscribeResponse(
        List<SequencedEvent> events
) {

    /**
     * Creates a new {@code SubscribeResponse}, treating a {@code null} event
     * list as empty.
     */
    public SubscribeResponse {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
