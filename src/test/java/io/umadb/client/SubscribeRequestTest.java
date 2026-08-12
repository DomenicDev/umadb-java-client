package io.umadb.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubscribeRequestTest {

    private static final Query QUERY = Query.empty();

    @Test
    void all_shouldCreateRequestWithDefaults() {
        SubscribeRequest request = SubscribeRequest.all();

        assertNull(request.query());
        assertNull(request.after());
        assertNull(request.batchSize());
    }

    @Test
    void of_shouldCreateRequestWithQuery() {
        SubscribeRequest request = SubscribeRequest.of(QUERY);

        assertEquals(QUERY, request.query());
        assertNull(request.after());
        assertNull(request.batchSize());
    }

    @Test
    void after_shouldReturnRequestWithUpdatedResumePoint() {
        SubscribeRequest request = SubscribeRequest.of(QUERY).after(100L);

        assertEquals(QUERY, request.query());
        assertEquals(100L, request.after());
    }

    @Test
    void withBatchSize_shouldReturnRequestWithUpdatedBatchSize() {
        SubscribeRequest request = SubscribeRequest.all().withBatchSize(25);

        assertEquals(25, request.batchSize());
    }

    @Test
    void constructor_shouldThrowException_forNegativeAfter() {
        assertThrows(IllegalArgumentException.class,
                () -> new SubscribeRequest(QUERY, -1L, null));
    }

    @Test
    void constructor_shouldThrowException_forZeroOrNegativeBatchSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new SubscribeRequest(QUERY, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SubscribeRequest(QUERY, null, -5));
    }
}
