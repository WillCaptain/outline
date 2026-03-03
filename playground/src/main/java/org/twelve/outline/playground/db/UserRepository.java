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

    private static final RowMapper<User> ROW_MAPPER = (rs, n) -> {
        User u = new User(rs.getString("id"), rs.getString("phone"));
        u.setUsername(rs.getString("username"));
        return u;
    };

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<User> findByPhone(String phone) {
        var list = jdbc.query("SELECT * FROM users WHERE phone = ?", ROW_MAPPER, phone);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<User> findById(String id) {
        var list = jdbc.query("SELECT * FROM users WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public User upsert(String phone) {
        return findByPhone(phone).orElseGet(() -> {
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO users (id, phone, created_at) VALUES (?,?,?)",
                    id, phone, System.currentTimeMillis());
            return new User(id, phone);
        });
    }

    public void updateUsername(String userId, String username) {
        jdbc.update("UPDATE users SET username = ? WHERE id = ?", username, userId);
    }

    // ── OTP ──────────────────────────────────────────────────────

    public void saveOtp(String phone, String code) {
        jdbc.update("""
            INSERT INTO otp_codes (phone, code, sent_at) VALUES (?,?,?)
            ON CONFLICT(phone) DO UPDATE SET code=excluded.code, sent_at=excluded.sent_at
            """, phone, code, System.currentTimeMillis());
    }

    public Optional<OtpRecord> findOtp(String phone) {
        var list = jdbc.query("SELECT code, sent_at FROM otp_codes WHERE phone = ?",
                (rs, n) -> new OtpRecord(rs.getString("code"), rs.getLong("sent_at")),
                phone);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void deleteOtp(String phone) {
        jdbc.update("DELETE FROM otp_codes WHERE phone = ?", phone);
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

    public record OtpRecord(String code, long sentAt) {}
}
