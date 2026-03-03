package org.twelve.outline.playground.model;

/** A comment attached to a code snippet identified by snippetId. */
public record Comment(
        String id,
        String snippetId,
        String userId,
        String displayName,
        /** "like" | "dislike" | "text" */
        String type,
        /** Non-null only for type="text" */
        String text,
        long createdAt
) {}
