package io.umadb.client;

import java.util.List;

public record AppendRequest(
        List<Event> events,
        AppendCondition condition
) {

}
