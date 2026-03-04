package org.twelve.outline.playground.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Creates all tables on startup if they do not yet exist.
 * SQLite is schema-light: we use a simple migration via IF NOT EXISTS.
 */
@Component
public class DatabaseInitializer {

    private final JdbcTemplate jdbc;

    public DatabaseInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void init() {
        // Enable WAL mode for better concurrent read/write (important for web apps)
        jdbc.execute("PRAGMA journal_mode=WAL");
        jdbc.execute("PRAGMA foreign_keys=ON");

        // Migration: if users has old phone schema, drop and recreate
        var tables = jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name='users'", String.class);
        if (!tables.isEmpty()) {
            var cols = jdbc.query("PRAGMA table_info(users)", (rs, n) -> rs.getString("name"));
            if (cols.contains("phone")) {
                jdbc.execute("DROP TABLE IF EXISTS comments");
                jdbc.execute("DROP TABLE IF EXISTS snippets");
                jdbc.execute("DROP TABLE IF EXISTS sessions");
                jdbc.execute("DROP TABLE IF EXISTS users");
                jdbc.execute("DROP TABLE IF EXISTS otp_codes");
            }
        }

        // Username + password (unique username, global users for sub-site shared login)
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id            TEXT PRIMARY KEY,
                username      TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                created_at    INTEGER NOT NULL
            )""");

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS sessions (
                token       TEXT PRIMARY KEY,
                user_id     TEXT NOT NULL,
                created_at  INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )""");


        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS snippets (
                id          TEXT PRIMARY KEY,
                user_id     TEXT NOT NULL,
                name        TEXT NOT NULL,
                code        TEXT NOT NULL,
                created_at  INTEGER NOT NULL,
                updated_at  INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )""");

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS comments (
                id           TEXT PRIMARY KEY,
                snippet_id   TEXT NOT NULL,
                user_id      TEXT NOT NULL,
                display_name TEXT NOT NULL,
                type         TEXT NOT NULL,
                text         TEXT,
                created_at   INTEGER NOT NULL
            )""");

        // Index for fast lookup by snippet
        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_comments_snippet
            ON comments (snippet_id)""");

        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_snippets_user
            ON snippets (user_id)""");
    }
}
