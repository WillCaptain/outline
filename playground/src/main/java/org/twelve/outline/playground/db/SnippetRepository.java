package org.twelve.outline.playground.db;

import org.twelve.outline.playground.model.Snippet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SnippetRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Snippet> ROW_MAPPER = (rs, n) -> new Snippet(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("name"),
            rs.getString("code"),
            rs.getLong("created_at"),
            rs.getLong("updated_at"));

    public SnippetRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Snippet> findByUserId(String userId) {
        return jdbc.query(
                "SELECT * FROM snippets WHERE user_id = ? ORDER BY updated_at DESC",
                ROW_MAPPER, userId);
    }

    public Snippet insert(String userId, String name, String code) {
        String id  = UUID.randomUUID().toString().replace("-", "");
        long   now = System.currentTimeMillis();
        jdbc.update("INSERT INTO snippets (id, user_id, name, code, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                id, userId, name, code, now, now);
        return new Snippet(id, userId, name, code, now, now);
    }

    public Optional<Snippet> update(String userId, String snippetId, String name, String code) {
        long now = System.currentTimeMillis();
        // Only update fields that are provided
        if (name != null && code != null) {
            jdbc.update("UPDATE snippets SET name=?, code=?, updated_at=? WHERE id=? AND user_id=?",
                    name, code, now, snippetId, userId);
        } else if (name != null) {
            jdbc.update("UPDATE snippets SET name=?, updated_at=? WHERE id=? AND user_id=?",
                    name, now, snippetId, userId);
        } else if (code != null) {
            jdbc.update("UPDATE snippets SET code=?, updated_at=? WHERE id=? AND user_id=?",
                    code, now, snippetId, userId);
        }
        return findById(userId, snippetId);
    }

    public Optional<Snippet> findById(String userId, String snippetId) {
        var list = jdbc.query(
                "SELECT * FROM snippets WHERE id=? AND user_id=?", ROW_MAPPER, snippetId, userId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean delete(String userId, String snippetId) {
        return jdbc.update("DELETE FROM snippets WHERE id=? AND user_id=?", snippetId, userId) > 0;
    }
}
