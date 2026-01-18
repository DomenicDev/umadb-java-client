package io.umadb.client;

/**
 * Represents a single event along with its position in the event stream.
 * <p>
 * The {@code position} indicates the sequence number of the event in the store,
 * which can be used for ordering, optimistic concurrency, or resuming reads.
 *
 * @param position the sequence number of the event in the event stream
 * @param event    the event payload
 */
public record SequencedEvent(
        long position,
        Event event
) {
}
