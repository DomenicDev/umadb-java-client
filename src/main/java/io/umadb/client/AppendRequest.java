package io.umadb.client;

import java.util.List;

/**
 * Represents a request to append one or more events.
 *
 * @param events
 *        the non-null, non-empty list of events to append
 * @param condition
 *        an optional {@link AppendCondition} that must be satisfied
 *        for the append operation to succeed; may be {@code null}
 * @param trackingInfo
 *        optional checkpointing information advancing the source's tracking
 *        cursor when the append succeeds; may be {@code null}
 */
public record AppendRequest(
        List<Event> events,
        AppendCondition condition,
        TrackingInfo trackingInfo
) {

    /**
     * Creates a new {@code AppendRequest}.
     *
     * @throws IllegalArgumentException if {@code events} is {@code null} or empty
     */
    public AppendRequest {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("events must not be null or empty");
        }
        // Defensive copy to preserve immutability
        events = List.copyOf(events);
    }

    /**
     * Creates an {@code AppendRequest} without tracking info.
     *
     * @param events    the events to append
     * @param condition the append condition to apply; may be {@code null}
     * @throws IllegalArgumentException if {@code events} is {@code null} or empty
     */
    public AppendRequest(List<Event> events, AppendCondition condition) {
        this(events, condition, null);
    }

    /**
     * Creates an {@code AppendRequest} without any append condition.
     *
     * @param events the events to append
     * @return a new {@code AppendRequest}
     * @throws IllegalArgumentException if {@code events} is {@code null} or empty
     */
    public static AppendRequest of(List<Event> events) {
        return new AppendRequest(events, null, null);
    }

    /**
     * Creates an {@code AppendRequest} with the given append condition.
     *
     * @param events    the events to append
     * @param condition the append condition to apply
     * @return a new {@code AppendRequest}
     * @throws IllegalArgumentException if {@code events} is {@code null} or empty
     */
    public static AppendRequest of(List<Event> events, AppendCondition condition) {
        return new AppendRequest(events, condition, null);
    }

    /**
     * Returns a copy of this request with the given append condition applied.
     *
     * @param condition the append condition to apply
     * @return a new {@code AppendRequest} with the specified condition
     */
    public AppendRequest withCondition(AppendCondition condition) {
        return new AppendRequest(this.events, condition, this.trackingInfo);
    }

    /**
     * Returns a copy of this request that also advances the given tracking cursor
     * when the append succeeds.
     *
     * @param trackingInfo the tracking info to checkpoint; may be {@code null}
     * @return a new {@code AppendRequest} with the specified tracking info
     */
    public AppendRequest withTrackingInfo(TrackingInfo trackingInfo) {
        return new AppendRequest(this.events, this.condition, trackingInfo);
    }
}
