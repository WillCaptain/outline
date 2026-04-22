package org.twelve.outlineaipp.tool;

import org.springframework.stereotype.Component;
import org.twelve.outlineaipp.OutlineRuntimeService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code outline_parse} — the cheapest, no-side-effect syntax check.
 *
 * <p>Intended as the LLM's first-line reality check before reaching for
 * {@code outline_infer} or {@code outline_interpret}. Typos, missing
 * braces, unbalanced parens — this tool surfaces all of them with
 * line-level messages, in a single round-trip.
 *
 * <p>The returned payload is intentionally minimal: two fields, no AST,
 * no token tree. The LLM does not need the AST to fix a typo, and emitting
 * one would balloon the context window for zero benefit.
 */
@Component
public class OutlineParseTool implements ToolHandler {

    private final OutlineRuntimeService runtime;

    public OutlineParseTool(OutlineRuntimeService runtime) {
        this.runtime = runtime;
    }

    @Override public String name() { return "outline_parse"; }

    @Override
    public Map<String, Object> describe() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", name());
        schema.put("description",
                "Syntax-check an Outline snippet. Returns {ok:true, errors:[]} when "
              + "the code parses cleanly, or {ok:false, errors:[...]} with human-readable "
              + "messages (one per collected parse error) otherwise. Pure function — no "
              + "side effects, no execution. Use this as the first step before "
              + "`outline_infer` / `outline_interpret` to catch typos and structural "
              + "mistakes cheaply.");

        Map<String, Object> codeProp = new LinkedHashMap<>();
        codeProp.put("type", "string");
        codeProp.put("description", "Outline source code to syntax-check.");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("code", codeProp);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("code"));
        params.put("additionalProperties", false);
        schema.put("parameters", params);

        schema.put("visibility", List.of("llm"));
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("level",        "universal");
        scope.put("visible_when", "always");
        schema.put("scope", scope);

        return schema;
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> args) {
        String code = args == null ? null : (String) args.get("code");
        OutlineRuntimeService.ParseResult r = runtime.parse(code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok",     r.ok());
        out.put("errors", r.errors());
        return out;
    }
}
