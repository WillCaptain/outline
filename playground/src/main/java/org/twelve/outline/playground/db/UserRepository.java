package org.twelve.outline.playground.db;

import org.twelve.outline.playground.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<User> ROW_MAPPER = (rs, n) ->
            new User(rs.getString("id"), rs.getString("username"), rs.getLong("created_at"));

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<User> findByUsername(String username) {
        var list = jdbc.query("SELECT * FROM users WHERE username = ?", ROW_MAPPER, username);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<User> findById(String id) {
        var list = jdbc.query("SELECT * FROM users WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** Creates a new user. Caller must ensure username is unique. */
    public User create(String username, String passwordHash) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users (id, username, password_hash, created_at) VALUES (?,?,?,?)",
                id, username, passwordHash, System.currentTimeMillis());
        return new User(id, username, System.currentTimeMillis());
    }

    public Optional<String> findPasswordHashByUsername(String username) {
        var list = jdbc.query("SELECT password_hash FROM users WHERE username = ?",
                (rs, n) -> rs.getString("password_hash"), username);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // ── Sessions ─────────────────────────────────────────────────

    public void saveSession(String token, String userId) {
        jdbc.update("INSERT INTO sessions (token, user_id, created_at) VALUES (?,?,?)",
                token, userId, System.currentTimeMillis());
    }

    public Optional<String> findUserIdByToken(String token) {
        var list = jdbc.query("SELECT user_id FROM sessions WHERE token = ?",
                (rs, n) -> rs.getString("user_id"), token);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void deleteSession(String token) {
        jdbc.update("DELETE FROM sessions WHERE token = ?", token);
    }
}
