package org.twelve.outline.playground.controller;

import org.twelve.outline.playground.model.User;
import org.twelve.outline.playground.service.SmsService;
import org.twelve.outline.playground.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;
    private final SmsService  smsService;

    public UserController(UserService userService, SmsService smsService) {
        this.userService = userService;
        this.smsService  = smsService;
    }

    /** POST /api/auth/send  { "phone": "13800138000" } */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@RequestBody Map<String, String> body) {
        String phone = body.getOrDefault("phone", "").trim();
        if (phone.isBlank() || !phone.matches("\\+?[0-9]{5,20}"))
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid phone number"));

        String code = userService.sendCode(phone);
        Map<String, Object> result = new HashMap<>();
        result.put("sent", true);
        // Only expose the OTP in the response when real SMS is disabled (dev mode).
        // In production (aliyun.sms.enabled=true) the code is sent via SMS only.
        if (!smsService.isEnabled()) {
            result.put("debug_code", code);
        }
        return ResponseEntity.ok(result);
    }

    /** POST /api/auth/verify  { "phone": "...", "code": "123456" } */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, String> body) {
        String phone = body.getOrDefault("phone", "").trim();
        String code  = body.getOrDefault("code",  "").trim();
        return userService.verify(phone, code)
                .map(entry -> {
                    String token = entry.getKey();
                    User u = entry.getValue();
                    return ResponseEntity.ok(userResponse(token, u));
                })
                .orElseGet(() -> ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid or expired code")));
    }

    /** GET /api/auth/me  (Authorization: Bearer <token>) */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return userService.getByToken(token(auth))
                .map(u -> ResponseEntity.ok(userResponse(token(auth), u)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /** PUT /api/auth/me  { "username": "..." } */
    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateMe(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {
        String tok = token(auth);
        String username = body.getOrDefault("username", "").trim();
        if (!userService.updateUsername(tok, username))
            return ResponseEntity.status(401).build();
        return userService.getByToken(tok)
                .map(u -> ResponseEntity.ok(userResponse(tok, u)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /** POST /api/auth/logout */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        userService.logout(token(auth));
        return ResponseEntity.ok().build();
    }

    // ── helpers ──────────────────────────────────────────────────

    private static String token(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7).trim();
    }

    private static Map<String, Object> userResponse(String token, User u) {
        return Map.of(
                "token",       token != null ? token : "",
                "id",          u.getId(),
                "phone",       u.maskedPhone(),
                "username",    u.getUsername() != null ? u.getUsername() : "",
                "displayName", u.displayName()
        );
    }
}
