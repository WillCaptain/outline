package org.twelve.outline.playground.model;

public class User {
    private final String id;
    private final String username;
    private final long createdAt;

    public User(String id, String username, long createdAt) {
        this.id = id;
        this.username = username;
        this.createdAt = createdAt;
    }

    public String getId()       { return id; }
    public String getUsername(){ return username; }
    public long   getCreatedAt(){ return createdAt; }

    public String displayName() {
        return username != null && !username.isBlank() ? username.trim() : "User";
    }
}
