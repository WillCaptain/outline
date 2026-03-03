package org.twelve.outline.playground.model;

/** A code snippet saved by a user to their personal MySpace. */
public record Snippet(
        String id,
        String userId,
        String name,
        String code,
        long createdAt,
        long updatedAt
) {}
