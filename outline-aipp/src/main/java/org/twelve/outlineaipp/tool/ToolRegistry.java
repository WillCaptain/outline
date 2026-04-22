package org.twelve.outlineaipp.tool;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects every Spring-managed {@link ToolHandler} bean and exposes them by
 * {@link ToolHandler#name()}. Autowired into {@code ToolsController} so the
 * controller stays agnostic of how many tools exist.
 *
 * <p>Handlers are kept in insertion order (Spring resolves constructor
 * injection of {@code List<ToolHandler>} in bean-registration order), which
 * is also the order surfaced by {@code /api/tools}. Stable order makes the
 * catalog diff-friendly across deploys.
 */
@Component
public class ToolRegistry {

    private final Map<String, ToolHandler> byName = new LinkedHashMap<>();

    public ToolRegistry(List<ToolHandler> handlers) {
        for (ToolHandler h : handlers) {
            ToolHandler prev = byName.put(h.name(), h);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate outline-aipp tool name: " + h.name()
                                + " (already registered by " + prev.getClass().getName() + ")");
            }
        }
    }

    /** Tool metadata list for {@code GET /api/tools}. */
    public List<Map<String, Object>> describeAll() {
        List<Map<String, Object>> out = new ArrayList<>(byName.size());
        for (ToolHandler h : byName.values()) out.add(h.describe());
        return out;
    }

    /** Find a handler by name, or {@code null} if the tool is unknown. */
    public ToolHandler find(String name) {
        return byName.get(name);
    }
}
