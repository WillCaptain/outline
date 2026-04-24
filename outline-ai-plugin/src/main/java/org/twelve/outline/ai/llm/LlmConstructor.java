package org.twelve.outline.ai.llm;

import org.twelve.gcp.interpreter.Interpreter;
import org.twelve.gcp.interpreter.SymbolConstructor;
import org.twelve.gcp.interpreter.value.EntityValue;
import org.twelve.gcp.interpreter.value.FunctionValue;
import org.twelve.gcp.interpreter.value.StringValue;
import org.twelve.gcp.interpreter.value.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code __llm__<T>} external-builder constructor.
 *
 * <p>Dispatch strategy: delegate default construction to the built-in
 * {@code external_builder} (which fills data fields from the outline
 * definition and its literal-type tags), then inspect the resulting
 * {@code provider} field to pick the matching {@link LlmProvider}, and
 * finally inject {@code ask} / {@code stream} methods as {@code this}-aware
 * builtins.
 *
 * <p>At call time, {@code MemberAccessor} binds the receiver EntityValue as
 * {@code this}, so the provider sees the user-supplied {@code model},
 * {@code api_key}, {@code base_url}, etc. — overrides layered on by the
 * surrounding {@code {field=value}} literal are automatically visible.
 */
public final class LlmConstructor implements SymbolConstructor {

    private final Interpreter interp;
    private final Map<String, LlmProvider> providers = new HashMap<>();

    public LlmConstructor(Interpreter interp) {
        this.interp = interp;
    }

    public LlmConstructor register(LlmProvider p) {
        providers.put(p.tag(), p);
        return this;
    }

    @Override
    public Value construct(String id, List<String> typeArgs, List<Value> valueArgs) {
        if (typeArgs.isEmpty()) {
            throw new RuntimeException("__llm__: missing type argument — use __llm__<OpenAI>{...}");
        }
        SymbolConstructor external = interp.constructors().get("external_builder");
        if (external == null) {
            throw new RuntimeException("__llm__: external_builder not registered on this interpreter");
        }

        Value base = external.construct("external_builder", typeArgs, valueArgs);
        if (!(base instanceof EntityValue ev)) {
            throw new RuntimeException("__llm__: could not build default instance of " + typeArgs.get(0));
        }

        Value providerVal = ev.get("provider");
        String tag = (providerVal instanceof StringValue sv) ? sv.value() : "";
        final LlmProvider p = providers.get(tag);
        if (p == null) {
            throw new RuntimeException("__llm__: no provider registered for tag '" + tag +
                    "' (type=" + typeArgs.get(0) + "); registered=" + providers.keySet());
        }

        ev.setField("ask", FunctionValue.thisAware((self, prompt) -> {
            EntityValue llm = (EntityValue) self;
            String promptStr = asString(prompt, "prompt");
            return new StringValue(p.ask(llm, promptStr));
        }));

        ev.setField("stream", FunctionValue.thisAware((self, prompt) -> {
            final EntityValue llm = (EntityValue) self;
            final String promptStr = asString(prompt, "prompt");
            return new FunctionValue((Value cb) -> {
                if (!(cb instanceof FunctionValue fv)) {
                    throw new RuntimeException("stream: callback must be a function, got " + cb);
                }
                return p.stream(llm, promptStr, fv, interp);
            });
        }));

        return ev;
    }

    private static String asString(Value v, String name) {
        if (v instanceof StringValue sv) return sv.value();
        throw new IllegalArgumentException(
                "__llm__: expected String for '" + name + "' but got " +
                        (v == null ? "null" : v.getClass().getSimpleName()));
    }
}
