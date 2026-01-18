package io.umadb.client;

import java.util.List;

/**
 * Represents a query composed of zero or more {@link QueryItem}s.
 * <p>
 * A {@code Query} is used to describe conditions for selecting or
 * matching events. An empty query matches all events.
 *
 * @param items
 *        a non-null list of query items; may be empty
 */
public record Query(List<QueryItem> items) {

    /**
     * Creates a new {@code Query}.
     *
     * @throws IllegalArgumentException if {@code items} is {@code null}
     */
    public Query {
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        // Defensive copy to preserve immutability
        items = List.copyOf(items);
    }

    /**
     * Creates an empty {@code Query} that matches all events.
     *
     * @return an empty {@code Query}
     */
    public static Query empty() {
        return new Query(List.of());
    }

    /**
     * Creates a {@code Query} containing a single query item.
     *
     * @param item the query item
     * @return a {@code Query} containing the given item
     * @throws IllegalArgumentException if {@code item} is {@code null}
     */
    public static Query of(QueryItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        return new Query(List.of(item));
    }

    /**
     * Creates a {@code Query} containing the given query items.
     *
     * @param items the query items
     * @return a {@code Query} containing the given items
     * @throws IllegalArgumentException if {@code items} is {@code null}
     */
    public static Query of(List<QueryItem> items) {
        return new Query(items);
    }

    /**
     * Returns a new {@code Query} with the given item appended.
     *
     * @param item the query item to add
     * @return a new {@code Query} including the given item
     * @throws IllegalArgumentException if {@code item} is {@code null}
     */
    public Query and(QueryItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        return new Query(
                new java.util.ArrayList<>() {{
                    addAll(items);
                    add(item);
                }}
        );
    }
}
