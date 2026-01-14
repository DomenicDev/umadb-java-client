package io.umadb.client;

import java.util.List;

public record ReadResponse(
        List<SequencedEvent> events,
        Long head
) {
}
