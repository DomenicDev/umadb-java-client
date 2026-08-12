package io.umadb.client;

import java.util.*;

/**
 * Represents a single immutable event.
 * <p>
 * An event consists of a type identifier, one or more tags for classification
 * or querying, an opaque binary payload, a unique event identifier, and
 * optional metadata carrying contextual information that is not part of the
 * payload itself (for example correlation IDs or user agents).
 *
 * @param type     the domain type or schema identifier of the event
 * @param tags     the indexed labels used for filtering; must contain at least one element
 * @param data     the serialized event payload
 * @param id       the unique event identifier, used for idempotency and deduplication
 * @param metadata additional context not part of the payload; never {@code null},
 *                 defaulting to an empty map
 */
public record Event(
        String type,
        List<String> tags,
        byte[] data,
        UUID id,
        Map<String, String> metadata
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
        // Preserve iteration order so metadata round-trips predictably
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * Creates a new {@code Event} without metadata.
     *
     * @param type the event type
     * @param tags the event tags (must contain at least one element)
     * @param data the event payload
     * @param id   the event identifier
     * @throws IllegalArgumentException if validation constraints are violated
     */
    public Event(String type, List<String> tags, byte[] data, UUID id) {
        this(type, tags, data, id, Map.of());
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
        return new Event(type, tags, data, UUID.randomUUID(), Map.of());
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
        return new Event(type, List.of(tag), data, UUID.randomUUID(), Map.of());
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
        return new Event(type, tags, data, id, Map.of());
    }

    /**
     * Returns a copy of this event with a different identifier.
     *
     * @param id the new event identifier
     * @return a new {@code Event} with the given identifier
     */
    public Event withId(UUID id) {
        return new Event(this.type, this.tags, this.data, id, this.metadata);
    }

    /**
     * Returns a copy of this event carrying the given metadata.
     *
     * @param metadata the metadata entries; may be {@code null} to clear
     * @return a new {@code Event} with the given metadata
     */
    public Event withMetadata(Map<String, String> metadata) {
        return new Event(this.type, this.tags, this.data, this.id, metadata);
    }

    /**
     * Returns a copy of this event with a single additional metadata entry.
     * <p>
     * An existing entry with the same key is replaced.
     *
     * @param key   the metadata key
     * @param value the metadata value
     * @return a new {@code Event} including the given entry
     * @throws IllegalArgumentException if {@code key} or {@code value} is {@code null}
     */
    public Event withMetadata(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        var merged = new LinkedHashMap<>(this.metadata);
        merged.put(key, value);
        return new Event(this.type, this.tags, this.data, this.id, merged);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(id, event.id)
                && Objects.equals(type, event.type)
                && Objects.deepEquals(data, event.data)
                && Objects.equals(tags, event.tags)
                && Objects.equals(metadata, event.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, tags, Arrays.hashCode(data), id, metadata);
    }

    @Override
    public String toString() {
        return "Event{" +
                "type='" + type + '\'' +
                ", tags=" + tags +
                ", data=" + Arrays.toString(data) +
                ", id=" + id +
                ", metadata=" + metadata +
                '}';
    }
}
