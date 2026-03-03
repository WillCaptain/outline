package org.twelve.outline.playground.db;

import org.twelve.outline.playground.model.Comment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CommentRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Comment> ROW_MAPPER = (rs, n) -> new Comment(
            rs.getString("id"),
            rs.getString("snippet_id"),
            rs.getString("user_id"),
            rs.getString("display_name"),
            rs.getString("type"),
            rs.getString("text"),
            rs.getLong("created_at"));

    public CommentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Comment> findBySnippetId(String snippetId) {
        return jdbc.query(
                "SELECT * FROM comments WHERE snippet_id = ? ORDER BY created_at ASC",
                ROW_MAPPER, snippetId);
    }

    public Comment insert(String snippetId, String userId, String displayName,
                          String type, String text) {
        String id  = UUID.randomUUID().toString().replace("-", "");
        long   now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO comments (id, snippet_id, user_id, display_name, type, text, created_at)
            VALUES (?,?,?,?,?,?,?)""",
                id, snippetId, userId, displayName, type,
                "text".equals(type) ? text : null, now);
        return new Comment(id, snippetId, userId, displayName, type,
                "text".equals(type) ? text : null, now);
    }

    /** Returns the reaction type the user already has for this snippet, or null. */
    public Optional<String> findUserReaction(String snippetId, String userId) {
        var list = jdbc.query("""
            SELECT type FROM comments
            WHERE snippet_id=? AND user_id=? AND type IN ('like','dislike')
            LIMIT 1""",
                (rs, n) -> rs.getString("type"), snippetId, userId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void deleteUserReactions(String snippetId, String userId) {
        jdbc.update("""
            DELETE FROM comments
            WHERE snippet_id=? AND user_id=? AND type IN ('like','dislike')
            """, snippetId, userId);
    }

    public Optional<Comment> findById(String commentId) {
        var list = jdbc.query("SELECT * FROM comments WHERE id=?", ROW_MAPPER, commentId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean delete(String commentId, String userId) {
        return jdbc.update("DELETE FROM comments WHERE id=? AND user_id=?", commentId, userId) > 0;
    }
}
