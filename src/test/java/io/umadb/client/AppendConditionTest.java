package io.umadb.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppendConditionTest {

    private static final Query QUERY = new Query(List.of(new QueryItem(List.of("type1"), List.of("tag1"))));

    @Test
    void constructor_shouldCreateCondition_whenArgumentsAreValid() {
        AppendCondition condition = new AppendCondition(QUERY, 10L);

        assertEquals(QUERY, condition.failIfEventsMatch());
        assertEquals(10L, condition.after());
    }

    @Test
    void constructor_shouldAllowNullAfter() {
        AppendCondition condition = new AppendCondition(QUERY, null);

        assertEquals(QUERY, condition.failIfEventsMatch());
        assertNull(condition.after());
    }

    @Test
    void constructor_shouldThrowException_whenQueryIsNull() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class,
                        () -> new AppendCondition(null, null));

        assertEquals("failIfEventsMatch must not be null", exception.getMessage());
    }

    @Test
    void constructor_shouldThrowException_whenAfterIsNegative() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class,
                        () -> new AppendCondition(QUERY, -1L));

        assertEquals("after must be >= 0", exception.getMessage());
    }

    @Test
    void failIfExists_shouldCreateConditionWithNullAfter() {
        AppendCondition condition = AppendCondition.failIfExists(QUERY);

        assertEquals(QUERY, condition.failIfEventsMatch());
        assertNull(condition.after());
    }

    @Test
    void failIfExists_shouldThrowException_whenQueryIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> AppendCondition.failIfExists(null));
    }

    @Test
    void failIfExistsAfter_shouldCreateConditionWithAfterValue() {
        AppendCondition condition =
                AppendCondition.failIfExistsAfter(QUERY, 42L);

        assertEquals(QUERY, condition.failIfEventsMatch());
        assertEquals(42L, condition.after());
    }

    @Test
    void failIfExistsAfter_shouldThrowException_whenAfterIsNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> AppendCondition.failIfExistsAfter(QUERY, -5L));
    }

    @Test
    void after_shouldReturnNewInstanceWithUpdatedAfter() {
        AppendCondition original =
                AppendCondition.failIfExists(QUERY);

        AppendCondition updated = original.after(100L);

        assertNotSame(original, updated);
        assertEquals(QUERY, updated.failIfEventsMatch());
        assertEquals(100L, updated.after());
        assertNull(original.after());
    }

    @Test
    void after_shouldThrowException_whenAfterIsNegative() {
        AppendCondition condition =
                AppendCondition.failIfExists(QUERY);

        assertThrows(IllegalArgumentException.class,
                () -> condition.after(-1L));
    }

}
