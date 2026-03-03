package org.twelve.outline.playground.model;

public class User {
    private final String id;
    private final String phone;
    private String username;
    private final long createdAt;

    public User(String id, String phone) {
        this.id = id;
        this.phone = phone;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId()       { return id; }
    public String getPhone()    { return phone; }
    public String getUsername() { return username; }
    public long   getCreatedAt(){ return createdAt; }

    public void setUsername(String username) { this.username = username; }

    /** Last 4 digits of phone, or explicit username if set. */
    public String displayName() {
        if (username != null && !username.isBlank()) return username.trim();
        return phone.length() >= 4 ? phone.substring(phone.length() - 4) : phone;
    }

    /** Masked phone: keep first 3 and last 4, replace middle with ****. */
    public String maskedPhone() {
        if (phone.length() <= 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
