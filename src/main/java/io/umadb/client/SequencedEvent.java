package io.umadb.client;

public record SequencedEvent(
        long position,
        Event event
) {
}
