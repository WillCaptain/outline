package org.twelve.outline.ai;

import org.twelve.gcp.interpreter.OutlineInterpreter;
import org.twelve.gcp.interpreter.value.EntityValue;
import org.twelve.gcp.interpreter.value.StringValue;
import org.twelve.gcp.interpreter.value.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Installs the {@code __llm__(model, prompt)} bridge on an
 * {@link OutlineInterpreter}.
 *
 * <p>The Outline return type is the {@code AiResult} ADT
 * ({@code Ok{text} | Denied{reason} | Timeout}).  This class handles the
 * conversion between host-side {@link AiResponse} records and the runtime
 * {@link EntityValue} representation used by pattern matching.
 *
 * <p>Usage:
 * <pre>
 *   OutlineInterpreter interp = new OutlineInterpreter();
 *   AiPlugin.install(interp, new MockAiPlugin().onAsk("hi", "hello"));
 * </pre>
 *
 * <p>The plugin id (without underscores) is {@code "llm"}.  The Outline side
 * ships with a small stdlib that builds {@code AiFlow} and {@code Agent} on
 * top of this single-arrow bridge — see {@code ai_flow.outline}.
 */
public final class AiPlugin {

    public static final String ID = "llm";

    private AiPlugin() {}

    /**
     * Registers {@code __llm__} on {@code interp} backed by {@code askFn}.
     * Returns the same interpreter for chaining.
     */
    public static OutlineInterpreter install(OutlineInterpreter interp, AskFn askFn) {
        interp.registerFunction(ID, 2, args -> {
            String model  = stringArg(args.get(0), "model");
            String prompt = stringArg(args.get(1), "prompt");
            AiResponse reply = askFn.ask(model, prompt);
            return toEntity(reply);
        });
        return interp;
    }

    /** Lifts an {@link AiResponse} into the runtime {@code AiResult} EntityValue. */
    public static Value toEntity(AiResponse reply) {
        if (reply instanceof AiResponse.Ok ok) {
            Map<String, Value> fields = new LinkedHashMap<>();
            fields.put("text", new StringValue(ok.text() == null ? "" : ok.text()));
            return new EntityValue("Ok", fields, null);
        }
        if (reply instanceof AiResponse.Denied denied) {
            Map<String, Value> fields = new LinkedHashMap<>();
            fields.put("reason", new StringValue(denied.reason() == null ? "" : denied.reason()));
            return new EntityValue("Denied", fields, null);
        }
        return new EntityValue("Timeout", new LinkedHashMap<>(), null);
    }

    private static String stringArg(Value v, String name) {
        if (v instanceof StringValue sv) return sv.value();
        throw new IllegalArgumentException(
                "__llm__: expected String for '" + name + "' but got " +
                (v == null ? "null" : v.getClass().getSimpleName()));
    }
}
