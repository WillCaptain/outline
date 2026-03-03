package org.twelve.outline.playground.controller;

import org.twelve.outline.playground.model.Snippet;
import org.twelve.outline.playground.model.User;
import org.twelve.outline.playground.service.SnippetService;
import org.twelve.outline.playground.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/snippets")
public class SnippetController {

    private final SnippetService snippetService;
    private final UserService    userService;

    public SnippetController(SnippetService snippetService, UserService userService) {
        this.snippetService = snippetService;
        this.userService    = userService;
    }

    /** GET /api/snippets — list current user's snippets */
    @GetMapping
    public ResponseEntity<List<Snippet>> list(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return requireUser(auth)
                .map(u -> ResponseEntity.ok(snippetService.getAll(u.getId())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /** POST /api/snippets  { "name": "...", "code": "..." } */
    @PostMapping
    public ResponseEntity<Snippet> save(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {
        return requireUser(auth).map(u -> {
            String name = body.getOrDefault("name", "Untitled").trim();
            String code = body.getOrDefault("code", "").trim();
            if (name.isBlank()) name = "Untitled";
            return ResponseEntity.ok(snippetService.save(u.getId(), name, code));
        }).orElseGet(() -> ResponseEntity.status(401).build());
    }

    /** PUT /api/snippets/{id}  { "name": "...", "code": "..." } */
    @PutMapping("/{id}")
    public ResponseEntity<Snippet> update(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        return requireUser(auth).flatMap(u -> {
            String name = body.get("name");
            String code = body.get("code");
            return snippetService.update(u.getId(), id, name, code);
        }).map(ResponseEntity::ok)
          .orElseGet(() -> ResponseEntity.status(404).build());
    }

    /** DELETE /api/snippets/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String id) {
        return requireUser(auth).map(u ->
                snippetService.delete(u.getId(), id)
                    ? ResponseEntity.ok().<Void>build()
                    : ResponseEntity.status(404).<Void>build()
        ).orElseGet(() -> ResponseEntity.status(401).build());
    }

    // ── helpers ──────────────────────────────────────────────────

    private Optional<User> requireUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return Optional.empty();
        return userService.getByToken(authHeader.substring(7).trim());
    }
}
