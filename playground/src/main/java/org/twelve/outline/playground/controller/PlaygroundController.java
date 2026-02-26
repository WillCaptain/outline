package org.twelve.outline.playground.controller;

import org.twelve.outline.playground.model.*;
import org.twelve.outline.playground.service.OutlineCompilerService;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PlaygroundController {

    private final OutlineCompilerService service;

    public PlaygroundController(OutlineCompilerService service) {
        this.service = service;
    }

    /** Full compile (parse + infer + execute). */
    @PostMapping("/run")
    public CompileResponse run(@RequestBody CompileRequest req) {
        return service.compile(req.code());
    }

    /** Return the built-in example library. */
    @GetMapping("/examples")
    public List<ExampleCode> examples() {
        return service.examples();
    }

    /**
     * Encode arbitrary code to a URL-safe base64 token for shareable links.
     * POST /api/share  { "code": "let x = 42;" }
     * → { "token": "bGV0IHggPSA0Mjs=" }
     */
    @PostMapping("/share")
    public Map<String, String> share(@RequestBody Map<String, String> body) {
        String code = body.getOrDefault("code", "");
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Map.of("token", token);
    }

    /**
     * Decode a share token back to source code.
     * GET /api/share/{token}
     * → { "code": "let x = 42;" }
     */
    @GetMapping("/share/{token}")
    public Map<String, String> load(@PathVariable String token) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token);
            String code = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return Map.of("code", code);
        } catch (Exception e) {
            return Map.of("code", "", "error", "Invalid share token");
        }
    }
}
