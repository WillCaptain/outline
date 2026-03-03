package org.twelve.outline.playground.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

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

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id          TEXT PRIMARY KEY,
                phone       TEXT NOT NULL UNIQUE,
                username    TEXT,
                created_at  INTEGER NOT NULL
            )""");

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS sessions (
                token       TEXT PRIMARY KEY,
                user_id     TEXT NOT NULL,
                created_at  INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )""");

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS otp_codes (
                phone       TEXT PRIMARY KEY,
                code        TEXT NOT NULL,
                sent_at     INTEGER NOT NULL
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
