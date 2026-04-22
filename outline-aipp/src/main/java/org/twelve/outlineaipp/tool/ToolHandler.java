package org.twelve.outlineaipp.tool;

import java.util.Map;

/**
 * Contract every atomic Outline tool implements.
 *
 * <p>Handlers self-register as Spring beans; {@code ToolRegistry} wires them
 * up by {@link #name()}. Keeping the schema + execution on the same object
 * prevents the common drift where {@code /api/tools} advertises a parameter
 * that {@code POST /api/tools/{name}} silently ignores.
 */
public interface ToolHandler {

    /** Tool name as it appears in {@code /api/tools} and is called by the LLM. */
    String name();

    /**
     * Tool metadata map exactly as it should appear in {@code GET /api/tools}.
     * Must include: {@code name}, {@code description}, {@code parameters},
     * {@code visibility}, {@code scope}. Implementations return a fresh map
     * so the caller may mutate it without affecting the next call.
     */
    Map<String, Object> describe();

    /**
     * Execute with {@code args} pulled from the POST body.
     * Returns the tool-specific JSON payload (never null). Errors should be
     * reported inside the payload, not as thrown exceptions, so the LLM can
     * reason about them without special handling in the host.
     */
    Map<String, Object> invoke(Map<String, Object> args);
}
