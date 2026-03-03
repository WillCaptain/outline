package org.twelve.outline.playground.service;

import org.twelve.outline.playground.db.CommentRepository;
import org.twelve.outline.playground.model.Comment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository repo;

    public CommentService(CommentRepository repo) { this.repo = repo; }

    public List<Comment> getComments(String snippetId) {
        return repo.findBySnippetId(snippetId);
    }

    /**
     * Adds a comment. For reactions (like/dislike) we toggle:
     * if the user already has the same reaction it is removed; otherwise the
     * previous reaction is replaced and the new one is stored.
     */
    public List<Comment> addComment(String snippetId, String userId,
                                    String displayName, String type, String text) {
        if ("like".equals(type) || "dislike".equals(type)) {
            String existing = repo.findUserReaction(snippetId, userId).orElse(null);
            repo.deleteUserReactions(snippetId, userId);
            if (type.equals(existing)) {
                // Same reaction clicked again → toggle off (just removed above)
                return repo.findBySnippetId(snippetId);
            }
        }
        repo.insert(snippetId, userId, displayName, type,
                "text".equals(type) ? text : null);
        return repo.findBySnippetId(snippetId);
    }

    public boolean deleteComment(String commentId, String userId) {
        return repo.delete(commentId, userId);
    }
}
