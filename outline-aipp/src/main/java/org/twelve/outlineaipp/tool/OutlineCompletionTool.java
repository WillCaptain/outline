package org.twelve.outlineaipp.tool;

import org.springframework.stereotype.Component;
import org.twelve.outlineaipp.CompletionEngine;
import org.twelve.outlineaipp.CompletionEngine.CompletionItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code outline_completion} — member / identifier suggestions at a cursor.
 *
 * <p>Intended workflow: when the LLM is drafting Outline and reaches a
 * dot-anchor like {@code virtualset.filter(a -> a.|)}, it calls this tool
 * with the buffer so far and the cursor offset. The payload lists valid
 * members on the receiver's inferred type — fields and methods alike.
 *
 * <p>If {@code offset} is absent the cursor is assumed to be at the end of
 * the buffer, which matches the most common incremental-drafting pattern.
 */
@Component
public class OutlineCompletionTool implements ToolHandler {

    private final CompletionEngine engine;

    public OutlineCompletionTool(CompletionEngine engine) {
        this.engine = engine;
    }

    @Override public String name() { return "outline_completion"; }

    @Override
    public Map<String, Object> describe() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", name());
        schema.put("description",
                "Return candidate members / identifiers at a cursor position in an "
              + "Outline snippet. Call this whenever you are about to write a `.` "
              + "and are unsure which fields/methods are available on the receiver "
              + "(e.g. at the `a.` inside `virtualset.filter(a -> a.|)`). Returns "
              + "`items:[{label, type, kind(method|property), description, origin}]`. "
              + "Empty list means the receiver could not be typed — try a more "
              + "complete prefix, or use `outline_grammar` for language-level help.");

        Map<String, Object> codeProp = new LinkedHashMap<>();
        codeProp.put("type", "string");
        codeProp.put("description", "Outline source buffer (may contain text after the cursor — it is ignored).");

        Map<String, Object> offsetProp = new LinkedHashMap<>();
        offsetProp.put("type", "integer");
        offsetProp.put("description",
                "Character offset of the cursor inside `code`. Omit (or pass -1) "
              + "to request completion at end-of-buffer.");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("code",   codeProp);
        props.put("offset", offsetProp);

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
        int offset = -1;
        if (args != null) {
            Object off = args.get("offset");
            if (off instanceof Number n) offset = n.intValue();
        }

        List<CompletionItem> items = engine.completionsAt(code, offset);
        List<Map<String, Object>> payload = new ArrayList<>(items.size());
        for (CompletionItem it : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", it.label());
            m.put("type",  it.type());
            m.put("kind",  it.kind());
            if (it.description() != null) m.put("description", it.description());
            if (it.origin()      != null) m.put("origin",      it.origin());
            payload.add(m);
        }
        return Map.of("items", payload);
    }
}
