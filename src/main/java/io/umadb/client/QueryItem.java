package io.umadb.client;

import java.util.List;

/**
 * Defines a single criterion for matching events.
 * <p>
 * A {@code QueryItem} matches an event if:
 * <ul>
 *   <li>at least one of its {@code types} matches the event type,
 *       or {@code types} is empty; <strong>and</strong></li>
 *   <li>all of its {@code tags} match one of the event tags,
 *       or {@code tags} is empty.</li>
 * </ul>
 *
 * <p>
 * Semantics:
 * <ul>
 *   <li>{@code types} represents a logical OR across event types</li>
 *   <li>{@code tags} represents a logical AND across event tags</li>
 * </ul>
 *
 * <p>
 * {@code QueryItem}s are combined in a {@link Query} to define
 * which events should be selected.
 *
 * @param types
 *        a non-null list of event types; may be empty
 * @param tags
 *        a non-null list of tags; may be empty
 */
public record QueryItem(
        List<String> types,
        List<String> tags
) {

    /**
     * Creates a new {@code QueryItem}.
     *
     * @throws IllegalArgumentException if {@code types} or {@code tags} is {@code null}
     */
    public QueryItem {
        if (types == null) {
            throw new IllegalArgumentException("types must not be null");
        }
        if (tags == null) {
            throw new IllegalArgumentException("tags must not be null");
        }

        types = List.copyOf(types);
        tags = List.copyOf(tags);
    }

    /**
     * Creates a {@code QueryItem} that matches all events.
     *
     * @return a {@code QueryItem} with no type or tag constraints
     */
    public static QueryItem matchAll() {
        return new QueryItem(List.of(), List.of());
    }

    /**
     * Creates a {@code QueryItem} that matches events of the given types.
     *
     * @param types the event types to match (logical OR)
     * @return a {@code QueryItem} with the given type constraints
     * @throws IllegalArgumentException if {@code types} is {@code null}
     */
    public static QueryItem ofTypes(List<String> types) {
        return new QueryItem(types, List.of());
    }

    /**
     * Creates a {@code QueryItem} that matches events containing all the given tags.
     *
     * @param tags the tags to match (logical AND)
     * @return a {@code QueryItem} with the given tag constraints
     * @throws IllegalArgumentException if {@code tags} is {@code null}
     */
    public static QueryItem ofTags(List<String> tags) {
        return new QueryItem(List.of(), tags);
    }

    /**
     * Creates a {@code QueryItem} that matches events that have any of the given types
     * and containing all the given tags.
     *
     * @param types the event types to match (logical OR)
     * @param tags  the tags to match (logical AND)
     * @return a {@code QueryItem} with both type and tag constraints
     * @throws IllegalArgumentException if {@code types} or {@code tags} is {@code null}
     */
    public static QueryItem of(List<String> types, List<String> tags) {
        return new QueryItem(types, tags);
    }
}