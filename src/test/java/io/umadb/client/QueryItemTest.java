package io.umadb.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryItemTest {

    @Test
    void constructor_shouldAllowEmptyLists() {
        QueryItem item = new QueryItem(List.of(), List.of());

        assertTrue(item.types().isEmpty());
        assertTrue(item.tags().isEmpty());
    }

    @Test
    void constructor_shouldThrowException_whenTypesIsNull() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class,
                        () -> new QueryItem(null, List.of()));

        assertEquals("types must not be null", exception.getMessage());
    }

    @Test
    void constructor_shouldThrowException_whenTagsIsNull() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class,
                        () -> new QueryItem(List.of(), null));

        assertEquals("tags must not be null", exception.getMessage());
    }

    @Test
    void lists_shouldBeUnmodifiable() {
        QueryItem item = new QueryItem(List.of("type1"), List.of("tag1"));

        assertThrows(UnsupportedOperationException.class,
                () -> item.types().add("type2"));

        assertThrows(UnsupportedOperationException.class,
                () -> item.tags().add("tag2"));
    }

    @Test
    void matchAll_shouldCreateItemWithNoConstraints() {
        QueryItem item = QueryItem.matchAll();

        assertTrue(item.types().isEmpty());
        assertTrue(item.tags().isEmpty());
    }

    @Test
    void ofTypes_shouldCreateItemWithTypesOnly() {
        QueryItem item = QueryItem.ofTypes(List.of("a", "b"));

        assertEquals(List.of("a", "b"), item.types());
        assertTrue(item.tags().isEmpty());
    }

    @Test
    void ofTags_shouldCreateItemWithTagsOnly() {
        QueryItem item = QueryItem.ofTags(List.of("x", "y"));

        assertTrue(item.types().isEmpty());
        assertEquals(List.of("x", "y"), item.tags());
    }

    @Test
    void of_shouldCreateItemWithTypesAndTags() {
        QueryItem item =
                QueryItem.of(List.of("type"), List.of("tag"));

        assertEquals(List.of("type"), item.types());
        assertEquals(List.of("tag"), item.tags());
    }

}
