package org.twelve.outlineaipp;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.twelve.outlineaipp.tool.ToolHandler;
import org.twelve.outlineaipp.tool.ToolRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AIPP Tool endpoints.
 *
 * <ul>
 *   <li>{@code GET  /api/tools}        — tool catalog, ordered by {@link ToolRegistry}.</li>
 *   <li>{@code POST /api/tools/{name}} — tool execution. Body: {@code {"args": {...}}}.</li>
 * </ul>
 *
 * <p>Every outline-aipp tool is {@code scope.level == "universal"} and
 * {@code visibility == ["llm"]} — pure, stateless language utilities with
 * no UI affordance and no widget binding. Any AIPP host can expose them.
 */
@RestController
@RequestMapping("/api")
public class ToolsController {

    /** Shared app metadata echoed in every AIPP discovery response. */
    static final String APP_ID      = "outline";
    static final String APP_VERSION = "1.0";

    private final ToolRegistry tools;

    public ToolsController(ToolRegistry tools) {
        this.tools = tools;
    }

    @GetMapping("/tools")
    public Map<String, Object> tools() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app",     APP_ID);
        result.put("version", APP_VERSION);
        result.put("system_prompt",
                "Outline is a strongly-typed functional DSL for ontology computation. "
              + "Use the `outline_code` skill (or the raw outline_* tools) to draft, "
              + "type-check and execute Outline snippets.");
        result.put("prompt_contributions", List.of());
        result.put("tools", tools.describeAll());
        return result;
    }

    @PostMapping("/tools/{name}")
    public ResponseEntity<Map<String, Object>> invokeTool(
            @PathVariable("name") String name,
            @RequestBody(required = false) Map<String, Object> body) {
        ToolHandler handler = tools.find(name);
        if (handler == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "unknown_tool",
                    "tool",  name
            ));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> args = body == null
                ? Map.of()
                : (Map<String, Object>) body.getOrDefault("args", Map.of());
        try {
            Map<String, Object> payload = handler.invoke(args);
            return ResponseEntity.ok(payload);
        } catch (RuntimeException e) {
            // Handlers are expected to surface their own errors in the payload;
            // this is the last-line guard that keeps the tool endpoint from ever
            // returning 5xx with a raw stack trace to the LLM / UI.
            return ResponseEntity.status(500).body(Map.of(
                    "error",   "tool_crashed",
                    "tool",    name,
                    "message", e.getClass().getSimpleName()
                            + (e.getMessage() != null ? ": " + e.getMessage() : "")
            ));
        }
    }
}
