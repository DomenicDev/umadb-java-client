package io.umadb.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryTest {


    private static final QueryItem ITEM1 = new QueryItem(
            List.of("type1"),
            List.of("tag1")
    );
    private static final QueryItem ITEM2 = new QueryItem(
            List.of("type2"),
            List.of("tag2")
    );

    @Test
    void constructor_shouldAllowEmptyList() {
        Query query = new Query(List.of());

        assertNotNull(query.items());
        assertTrue(query.items().isEmpty());
    }

    @Test
    void constructor_shouldThrowException_whenItemsIsNull() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class,
                        () -> new Query(null));

        assertEquals("items must not be null", exception.getMessage());
    }

    @Test
    void itemsList_shouldBeUnmodifiable() {
        Query query = new Query(List.of(ITEM1));

        assertThrows(UnsupportedOperationException.class,
                () -> query.items().add(ITEM2));
    }

    @Test
    void empty_shouldCreateEmptyQuery() {
        Query query = Query.empty();

        assertNotNull(query.items());
        assertTrue(query.items().isEmpty());
    }

    @Test
    void ofSingleItem_shouldCreateQueryWithOneItem() {
        Query query = Query.of(ITEM1);

        assertEquals(List.of(ITEM1), query.items());
    }

    @Test
    void ofSingleItem_shouldThrowException_whenItemIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> Query.of((QueryItem) null));
    }

    @Test
    void ofList_shouldCreateQueryWithItems() {
        Query query = Query.of(List.of(ITEM1, ITEM2));

        assertEquals(List.of(ITEM1, ITEM2), query.items());
    }

    @Test
    void and_shouldReturnNewQueryWithAppendedItem() {
        Query original = Query.of(ITEM1);

        Query updated = original.and(ITEM2);

        assertNotSame(original, updated);
        assertEquals(List.of(ITEM1, ITEM2), updated.items());
        assertEquals(List.of(ITEM1), original.items());
    }

    @Test
    void and_shouldThrowException_whenItemIsNull() {
        Query query = Query.empty();

        assertThrows(IllegalArgumentException.class,
                () -> query.and(null));
    }

}
