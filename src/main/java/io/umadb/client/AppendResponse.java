package io.umadb.client;

/**
 * Represents the result of a successful append operation.
 * <p>
 * The {@code position} indicates the position of the last appended event
 * after the append operation completes. This value can be used by clients
 * for optimistic concurrency control or as a reference point for subsequent
 * append or read operations.
 *
 * @param position
 *        the position of the last appended event
 */
public record AppendResponse(long position) {
}
