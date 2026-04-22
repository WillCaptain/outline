package org.twelve.outlineaipp.tool;

import org.springframework.stereotype.Component;
import org.twelve.outlineaipp.OutlineRuntimeService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code outline_interpret} — parse, type-check, and execute an Outline snippet.
 *
 * <p>Semantics are strictly gated: if {@code syntax_errors} or {@code infer_errors}
 * is non-empty the interpreter is <em>not</em> invoked. Running ill-typed code
 * tends to either crash opaquely or (worse) produce a confidently-wrong
 * "result" derived from partially-inferred stubs; neither is actionable for
 * the LLM.
 *
 * <p>Return payload:
 * <pre>
 * { ok, syntax_errors[], infer_errors[], runtime_error?, result? }
 * </pre>
 * where {@code result} (when present) carries {@code display} / {@code type} /
 * {@code value} for the final expression.
 */
@Component
public class OutlineInterpretTool implements ToolHandler {

    private final OutlineRuntimeService runtime;

    public OutlineInterpretTool(OutlineRuntimeService runtime) {
        this.runtime = runtime;
    }

    @Override public String name() { return "outline_interpret"; }

    @Override
    public Map<String, Object> describe() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", name());
        schema.put("description",
                "Parse + type-check + execute an Outline snippet. Refuses to run if "
              + "syntax_errors or infer_errors are non-empty. On success, `result.display` "
              + "is a human-readable rendering and `result.value` is the JSON-safe scalar "
              + "(when applicable). Use as the final verification step after "
              + "`outline_parse` and `outline_infer` return ok=true.");

        Map<String, Object> codeProp = new LinkedHashMap<>();
        codeProp.put("type", "string");
        codeProp.put("description", "Outline source code to execute.");

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
        OutlineRuntimeService.InterpretResult r = runtime.interpret(code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok",            r.ok());
        out.put("syntax_errors", r.syntax_errors());
        out.put("infer_errors",  r.infer_errors());
        if (r.runtime_error() != null) out.put("runtime_error", r.runtime_error());
        if (r.result()        != null) out.put("result",        r.result());
        return out;
    }
}
