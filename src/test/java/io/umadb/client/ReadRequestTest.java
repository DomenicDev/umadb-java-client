package io.umadb.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReadRequestTest {

    private static final Query QUERY = Query.empty();

    @Test
    void all_shouldCreateRequestWithDefaults() {
        ReadRequest request = ReadRequest.all();

        assertNull(request.query());
        assertNull(request.start());
        assertFalse(request.backwards());
        assertNull(request.limit());
        assertFalse(request.subscribe());
        assertNull(request.batchSize());
    }

    @Test
    void of_shouldCreateRequestWithQuery() {
        ReadRequest request = ReadRequest.of(QUERY);

        assertEquals(QUERY, request.query());
        assertNull(request.start());
        assertFalse(request.backwards());
        assertNull(request.limit());
        assertFalse(request.subscribe());
        assertNull(request.batchSize());
    }

    @Test
    void withStart_shouldReturnRequestWithUpdatedStart() {
        ReadRequest request = ReadRequest.all().withStart(100L);

        assertEquals(100L, request.start());
    }

    @Test
    void withLimit_shouldReturnRequestWithUpdatedLimit() {
        ReadRequest request = ReadRequest.all().withLimit(50);

        assertEquals(50, request.limit());
    }

    @Test
    void withDirection_shouldReturnRequestWithUpdatedDirection() {
        ReadRequest request = ReadRequest.all().withDirection(true);

        assertTrue(request.backwards());
    }

    @Test
    void subscribe_shouldReturnRequestWithSubscriptionEnabled() {
        ReadRequest request = ReadRequest.all().subscribe(10);

        assertTrue(request.subscribe());
        assertEquals(10, request.batchSize());
    }

    @Test
    void constructor_shouldThrowException_forNegativeStart() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReadRequest(QUERY, -1L, null, null, null, null));
    }

    @Test
    void constructor_shouldThrowException_forZeroOrNegativeLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReadRequest(QUERY, null, null, 0, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadRequest(QUERY, null, null, -1, null, null));
    }

    @Test
    void constructor_shouldThrowException_forZeroOrNegativeBatchSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReadRequest(QUERY, null, null, null, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadRequest(QUERY, null, null, null, true, -5));
    }

}
