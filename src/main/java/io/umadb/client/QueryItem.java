package io.umadb.client;

import java.util.List;

public record QueryItem(
        List<String> types,
        List<String> tags
) {
}
