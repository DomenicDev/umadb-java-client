package io.umadb.client;

public record AppendCondition(
        Query failIfEventsMatch,
        long after
) {
}
