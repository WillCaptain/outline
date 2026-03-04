package org.twelve.outline.playground.controller;

import org.twelve.outline.playground.model.User;
import org.twelve.outline.playground.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** POST /api/auth/register  { "username": "alice", "password": "secret" } */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");

        return userService.register(username, password)
                .map(entry -> {
                    String token = entry.getKey();
                    User u = entry.getValue();
                    return ResponseEntity.ok(userResponse(token, u));
                })
                .orElseGet(() -> ResponseEntity.badRequest()
                        .body(Map.of("error", "Username taken or invalid (use a-z, 0-9, _, 4-32 chars, password 4-64 chars)")));
    }

    /** POST /api/auth/login  { "username": "alice", "password": "secret" } */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");

        return userService.login(username, password)
                .map(entry -> {
                    String token = entry.getKey();
                    User u = entry.getValue();
                    return ResponseEntity.ok(userResponse(token, u));
                })
                .orElseGet(() -> ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid username or password")));
    }

    /** GET /api/auth/me  (Authorization: Bearer <token>) */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return userService.getByToken(token(auth))
                .map(u -> ResponseEntity.ok(userResponse(token(auth), u)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /** POST /api/auth/logout */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        userService.logout(token(auth));
        return ResponseEntity.ok().build();
    }

    private static String token(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7).trim();
    }

    private static Map<String, Object> userResponse(String token, User u) {
        return Map.of(
                "token",       token != null ? token : "",
                "id",          u.getId(),
                "username",    u.getUsername(),
                "displayName", u.displayName()
        );
    }
}
