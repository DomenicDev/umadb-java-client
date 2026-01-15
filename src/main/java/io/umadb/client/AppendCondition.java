package io.umadb.client;

public record AppendCondition(
        Query failIfEventsMatch,
        Long after
) {
}
