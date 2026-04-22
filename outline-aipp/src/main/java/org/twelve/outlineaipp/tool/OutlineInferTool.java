package org.twelve.outlineaipp.tool;

import org.springframework.stereotype.Component;
import org.twelve.outlineaipp.OutlineRuntimeService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code outline_infer} — run the Outline type checker without executing anything.
 *
 * <p>Returns two parallel error lists so the LLM can tell a typo from a type
 * mismatch at a glance:
 * <ul>
 *   <li>{@code syntax_errors} — same normalized markers as {@code outline_parse}.
 *       Non-empty {@code syntax_errors} always means {@code ok == false}, and
 *       type inference is skipped (type-checking gibberish is noise).</li>
 *   <li>{@code infer_errors} — type mismatches, undefined symbols, etc.,
 *       rendered by {@link org.twelve.gcp.exception.GCPError#toString} with
 *       location and code snippet already attached.</li>
 * </ul>
 */
@Component
public class OutlineInferTool implements ToolHandler {

    private final OutlineRuntimeService runtime;

    public OutlineInferTool(OutlineRuntimeService runtime) {
        this.runtime = runtime;
    }

    @Override public String name() { return "outline_infer"; }

    @Override
    public Map<String, Object> describe() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", name());
        schema.put("description",
                "Type-check an Outline snippet (parse + inference, no execution). "
              + "Returns {ok, syntax_errors[], infer_errors[]}. Use this after "
              + "`outline_parse` passes, before committing to `outline_interpret`. "
              + "Pure function — no side effects.");

        Map<String, Object> codeProp = new LinkedHashMap<>();
        codeProp.put("type", "string");
        codeProp.put("description", "Outline source code to type-check.");

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
        scope.put("level", "universal");
        scope.put("visible_when", "always");
        schema.put("scope", scope);
        return schema;
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> args) {
        String code = args == null ? null : (String) args.get("code");
        OutlineRuntimeService.InferResult r = runtime.infer(code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok",            r.ok());
        out.put("syntax_errors", r.syntax_errors());
        out.put("infer_errors",  r.infer_errors());
        return out;
    }
}
