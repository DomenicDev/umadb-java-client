package io.umadb.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackingInfoTest {

    private static final String SOURCE = "order-projection";

    @Test
    void of_shouldCreateTrackingInfo() {
        TrackingInfo trackingInfo = TrackingInfo.of(SOURCE, 42L);

        assertEquals(SOURCE, trackingInfo.source());
        assertEquals(42L, trackingInfo.position());
    }

    @Test
    void constructor_shouldAllowZeroPosition() {
        assertEquals(0L, new TrackingInfo(SOURCE, 0L).position());
    }

    @Test
    void constructor_shouldThrowException_forNullOrBlankSource() {
        assertThrows(IllegalArgumentException.class, () -> new TrackingInfo(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> new TrackingInfo("   ", 1L));
    }

    @Test
    void constructor_shouldThrowException_forNegativePosition() {
        assertThrows(IllegalArgumentException.class, () -> new TrackingInfo(SOURCE, -1L));
    }

    @Test
    void at_shouldReturnTrackingInfoWithUpdatedPosition() {
        TrackingInfo advanced = TrackingInfo.of(SOURCE, 1L).at(99L);

        assertEquals(SOURCE, advanced.source());
        assertEquals(99L, advanced.position());
    }
}
