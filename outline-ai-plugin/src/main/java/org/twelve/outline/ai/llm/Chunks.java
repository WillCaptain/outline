package org.twelve.outline.ai.llm;

import org.twelve.gcp.interpreter.value.EntityValue;
import org.twelve.gcp.interpreter.value.StringValue;
import org.twelve.gcp.interpreter.value.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builders for {@code Chunk = Delta{text} | Done | Error{reason}} EntityValues. */
public final class Chunks {
    private Chunks() {}

    public static Value delta(String text) {
        Map<String, Value> f = new LinkedHashMap<>();
        f.put("text", new StringValue(text == null ? "" : text));
        return new EntityValue("Delta", f, null);
    }

    public static Value done() {
        return new EntityValue("Done", new LinkedHashMap<>(), null);
    }

    public static Value error(String reason) {
        Map<String, Value> f = new LinkedHashMap<>();
        f.put("reason", new StringValue(reason == null ? "" : reason));
        return new EntityValue("Error", f, null);
    }
}
