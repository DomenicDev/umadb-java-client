package io.umadb.client;

import java.util.List;

public record Query(List<QueryItem> items) {
}
