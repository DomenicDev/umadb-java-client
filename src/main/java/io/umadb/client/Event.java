package io.umadb.client;

import java.util.List;
import java.util.UUID;

/**
 * Represents a single immutable event.
 * <p>
 * An event consists of a type identifier, one or more tags for classification
 * or querying, an opaque binary payload, and a unique event identifier.
 */
public record Event(
        String type,
        List<String> tags,
        byte[] data,
        UUID id
) {

    /**
     * Creates a new {@code Event}.
     *
     * @throws IllegalArgumentException if validation constraints are violated
     */
    public Event {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be null or blank");
        }
        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException("tags must not be null or empty");
        }
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }

        tags = List.copyOf(tags);
    }

    /**
     * Creates a new {@code Event} with a generated identifier.
     *
     * @param type the event type
     * @param tags the event tags (must contain at least one element)
     * @param data the event payload
     * @return a new {@code Event} with a random UUID
     */
    public static Event of(
            String type,
            List<String> tags,
            byte[] data
    ) {
        return new Event(type, tags, data, UUID.randomUUID());
    }

    /**
     * Creates a new {@code Event} with a generated identifier and a single tag.
     *
     * @param type the event type
     * @param tag  the single event tag
     * @param data the event payload
     * @return a new {@code Event} with a random UUID
     */
    public static Event of(
            String type,
            String tag,
            byte[] data
    ) {
        return new Event(type, List.of(tag), data, UUID.randomUUID());
    }

    /**
     * Creates a new {@code Event} with the given identifier.
     * <p>
     * This factory is useful when events are reconstructed from
     * persisted storage or received from a remote source.
     *
     * @param type the event type
     * @param tags the event tags
     * @param data the event payload
     * @param id   the event identifier
     * @return a new {@code Event}
     */
    public static Event withId(
            String type,
            List<String> tags,
            byte[] data,
            UUID id
    ) {
        return new Event(type, tags, data, id);
    }

    /**
     * Returns a copy of this event with a different identifier.
     *
     * @param id the new event identifier
     * @return a new {@code Event} with the given identifier
     */
    public Event withId(UUID id) {
        return new Event(this.type, this.tags, this.data, id);
    }

}
