package io.umadb.client;

/**
 * Represents the read/processing progress of a named consumer or projection.
 * <p>
 * Tracking info lets a consumer checkpoint how far it has processed the event
 * stream. It can be attached to an {@link AppendRequest} to advance the cursor
 * atomically with the append, and read back later via
 * {@link UmaDbClient#getTrackingInfo(String)}.
 *
 * @param source   the unique identifier of the consumer, projection, or system
 *                 tracking the events
 * @param position the sequence position up to which the source has successfully
 *                 processed
 */
public record TrackingInfo(
        String source,
        long position
) {

    /**
     * Creates a new {@code TrackingInfo}.
     *
     * @throws IllegalArgumentException if validation constraints are violated
     */
    public TrackingInfo {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be null or blank");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position must be >= 0");
        }
    }

    /**
     * Creates a new {@code TrackingInfo}.
     *
     * @param source   the consumer identifier
     * @param position the position processed up to
     * @return a new {@code TrackingInfo}
     */
    public static TrackingInfo of(String source, long position) {
        return new TrackingInfo(source, position);
    }

    /**
     * Returns a copy of this tracking info advanced to the given position.
     *
     * @param position the new position
     * @return a new {@code TrackingInfo} for the same source
     */
    public TrackingInfo at(long position) {
        return new TrackingInfo(this.source, position);
    }
}
