package org.twelve.outline.playground.controller;

import org.twelve.outline.playground.model.Comment;
import org.twelve.outline.playground.model.User;
import org.twelve.outline.playground.service.CommentService;
import org.twelve.outline.playground.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;
    private final UserService    userService;

    public CommentController(CommentService commentService, UserService userService) {
        this.commentService = commentService;
        this.userService    = userService;
    }

    /** GET /api/comments?snippetId=xxx */
    @GetMapping
    public List<Comment> list(@RequestParam String snippetId) {
        return commentService.getComments(snippetId);
    }

    /**
     * POST /api/comments
     * Body: { "snippetId": "...", "type": "like"|"dislike"|"text", "text": "..." }
     */
    @PostMapping
    public ResponseEntity<List<Comment>> add(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {

        Optional<User> user = userService.getByToken(token(auth));
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        String snippetId = body.getOrDefault("snippetId", "").trim();
        String type      = body.getOrDefault("type",      "text").trim();
        String text      = body.getOrDefault("text",      "").trim();

        if (snippetId.isBlank()) return ResponseEntity.badRequest().build();
        if (!List.of("like", "dislike", "text").contains(type))
            return ResponseEntity.badRequest().build();
        if ("text".equals(type) && text.isBlank()) return ResponseEntity.badRequest().build();

        User u = user.get();
        List<Comment> updated = commentService.addComment(snippetId, u.getId(), u.displayName(), type, text);
        return ResponseEntity.ok(updated);
    }

    /** DELETE /api/comments/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String id) {

        Optional<User> user = userService.getByToken(token(auth));
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        boolean ok = commentService.deleteComment(id, user.get().getId());
        return ok ? ResponseEntity.ok().build() : ResponseEntity.status(403).build();
    }

    private static String token(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7).trim();
    }
}
