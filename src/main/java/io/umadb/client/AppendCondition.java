package io.umadb.client;

/**
 * Represents a conditional constraint applied when appending events.
 * <p>
 * The append operation will fail if events matching the provided {@code failIfEventsMatch}
 * query already exist. Optionally, the condition can be further constrained to only
 * consider events occurring after a given position.
 *
 * @param failIfEventsMatch
 *        a non-null query defining events that must not already exist
 *        for the append operation to succeed
 * @param after
 *        an optional position after which the condition applies;
 *        may be {@code null} to indicate no lower bound
 */
public record AppendCondition(
        Query failIfEventsMatch,
        Long after
) {

    /**
     * Creates a new {@code AppendCondition}.
     *
     * @throws IllegalArgumentException if {@code failIfEventsMatch} is {@code null}
     */
    public AppendCondition {
        if (failIfEventsMatch == null) {
            throw new IllegalArgumentException("failIfEventsMatch must not be null");
        }
        if (after != null && after < 0) {
            throw new IllegalArgumentException("after must be >= 0");
        }
    }

    /**
     * Creates an {@code AppendCondition} that fails the append operation
     * if any events matching the given query already exist.
     *
     * @param query the query defining events that must not exist
     * @return a new {@code AppendCondition}
     * @throws IllegalArgumentException if {@code query} is {@code null}
     */
    public static AppendCondition failIfExists(Query query) {
        return new AppendCondition(query, null);
    }

    /**
     * Creates an {@code AppendCondition} that fails the append operation
     * if events matching the given query exist after the specified position.
     *
     * @param query the query defining events that must not exist
     * @param after the lower bound (position or timestamp) after which
     *              matching events are considered
     * @return a new {@code AppendCondition}
     * @throws IllegalArgumentException if {@code query} is {@code null}
     */
    public static AppendCondition failIfExistsAfter(Query query, long after) {
        return new AppendCondition(query, after);
    }

    /**
     * Returns a copy of this condition that applies only to events
     * occurring after the specified position or timestamp.
     * <p>
     * This method is useful for fluently refining an existing condition.
     *
     * @param after the lower bound (position or timestamp)
     * @return a new {@code AppendCondition} with the updated {@code after} value
     */
    public AppendCondition after(long after) {
        return new AppendCondition(this.failIfEventsMatch, after);
    }

}
