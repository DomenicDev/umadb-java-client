package io.umadb.client;

import java.util.List;
import java.util.UUID;

public record Event(
        String type,
        List<String> tags,
        byte[] data,
        UUID id
) {
}
