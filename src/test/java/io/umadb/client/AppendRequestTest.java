package io.umadb.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class AppendRequestTest {

    // Simple stubs
    private static final Event EVENT = new Event(
            "type",
            List.of("tag1"),
            "TestData".getBytes(UTF_8),
            UUID.randomUUID()
    );

    private static final AppendCondition CONDITION =
            AppendCondition.failIfExists(new Query(List.of(new QueryItem(List.of("type1"), List.of("tag1")))));

    @Test
    void constructor_shouldCreateRequest_whenArgumentsAreValid() {
        List<Event> events = List.of(EVENT);

        AppendRequest request = new AppendRequest(events, CONDITION);

        assertEquals(events, request.events());
        assertEquals(CONDITION, request.condition());
    }

    @Test
    void constructor_shouldAllowNullCondition() {
        List<Event> events = List.of(EVENT);

        AppendRequest request = new AppendRequest(events, null);

        assertEquals(events, request.events());
        assertNull(request.condition());
    }

    @Test
    void constructor_shouldThrowException_whenEventsIsNull() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class,
                        () -> new AppendRequest(null, null));

        assertEquals("events must not be null or empty", exception.getMessage());
    }

    @Test
    void constructor_shouldThrowException_whenEventsIsEmpty() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class,
                        () -> new AppendRequest(List.of(), null));

        assertEquals("events must not be null or empty", exception.getMessage());
    }

    @Test
    void eventsList_shouldBeUnmodifiable() {
        List<Event> mutableEvents = List.of(EVENT);
        AppendRequest request = new AppendRequest(mutableEvents, null);

        assertThrows(UnsupportedOperationException.class,
                () -> request.events().add(EVENT));
    }

    @Test
    void of_shouldCreateRequestWithoutCondition() {
        List<Event> events = List.of(EVENT);

        AppendRequest request = AppendRequest.of(events);

        assertEquals(events, request.events());
        assertNull(request.condition());
    }

    @Test
    void of_shouldCreateRequestWithCondition() {
        List<Event> events = List.of(EVENT);

        AppendRequest request = AppendRequest.of(events, CONDITION);

        assertEquals(events, request.events());
        assertEquals(CONDITION, request.condition());
    }

    @Test
    void withCondition_shouldReturnNewInstanceWithCondition() {
        List<Event> events = List.of(EVENT);
        AppendRequest original = AppendRequest.of(events);

        AppendRequest updated = original.withCondition(CONDITION);

        assertNotSame(original, updated);
        assertEquals(events, updated.events());
        assertEquals(CONDITION, updated.condition());
        assertNull(original.condition());
    }
}
