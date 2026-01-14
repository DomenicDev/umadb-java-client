package io.umadb.client;

public record ReadRequest(
        Query query,
        Long start,
        Boolean backwards,
        Integer limit,
        Boolean subscribe,
        Integer batchSize
) {
}
