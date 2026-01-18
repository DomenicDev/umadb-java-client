package io.umadb.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    private static final String TYPE = "user.created";
    private static final String TAG = "user";
    private static final byte[] DATA = new byte[]{1, 2, 3};

    @Test
    void constructor_shouldCreateEvent_whenAllFieldsAreValid() {
        UUID id = UUID.randomUUID();
        List<String> tags = List.of("a", "b");

        Event event = new Event(TYPE, tags, DATA, id);

        assertEquals(TYPE, event.type());
        assertEquals(tags, event.tags());
        assertArrayEquals(DATA, event.data());
        assertEquals(id, event.id());
    }

    @Test
    void constructor_shouldDefensivelyCopyTags() {
        List<String> mutableTags = List.of("a");

        Event event = new Event(TYPE, mutableTags, DATA, UUID.randomUUID());

        assertThrows(UnsupportedOperationException.class,
                () -> event.tags().add("b"));
    }

    @Test
    void constructor_shouldThrowException_whenTypeIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Event(null, List.of("a"), DATA, UUID.randomUUID()));
    }

    @Test
    void constructor_shouldThrowException_whenTypeIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new Event("   ", List.of("a"), DATA, UUID.randomUUID()));
    }

    @Test
    void constructor_shouldThrowException_whenTagsIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Event(TYPE, null, DATA, UUID.randomUUID()));
    }

    @Test
    void constructor_shouldThrowException_whenTagsIsEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> new Event(TYPE, List.of(), DATA, UUID.randomUUID()));
    }

    @Test
    void constructor_shouldThrowException_whenDataIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Event(TYPE, List.of("a"), null, UUID.randomUUID()));
    }

    @Test
    void constructor_shouldThrowException_whenIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Event(TYPE, List.of("a"), DATA, null));
    }

    @Test
    void of_shouldCreateEventWithGeneratedId_andMultipleTags() {
        Event event = Event.of(TYPE, List.of("a", "b"), DATA);

        assertEquals(TYPE, event.type());
        assertEquals(List.of("a", "b"), event.tags());
        assertArrayEquals(DATA, event.data());
        assertNotNull(event.id());
    }

    @Test
    void of_shouldCreateEventWithGeneratedId_andSingleTag() {
        Event event = Event.of(TYPE, TAG, DATA);

        assertEquals(TYPE, event.type());
        assertEquals(List.of(TAG), event.tags());
        assertArrayEquals(DATA, event.data());
        assertNotNull(event.id());
    }

    @Test
    void withIdFactory_shouldUseProvidedId() {
        UUID id = UUID.randomUUID();

        Event event = Event.withId(TYPE, List.of("a"), DATA, id);

        assertEquals(id, event.id());
    }

    @Test
    void withIdInstanceMethod_shouldReturnNewEventWithUpdatedId() {
        Event original = Event.of(TYPE, TAG, DATA);
        UUID newId = UUID.randomUUID();

        Event updated = original.withId(newId);

        assertNotSame(original, updated);
        assertEquals(newId, updated.id());
        assertEquals(original.type(), updated.type());
        assertEquals(original.tags(), updated.tags());
        assertArrayEquals(original.data(), updated.data());
    }

}
