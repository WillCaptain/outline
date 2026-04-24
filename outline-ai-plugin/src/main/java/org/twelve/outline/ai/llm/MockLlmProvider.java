package org.twelve.outline.ai.llm;

import org.twelve.gcp.interpreter.Interpreter;
import org.twelve.gcp.interpreter.value.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * In-memory {@link LlmProvider} for tests.  Pre-program prompt→answer mappings
 * and it will echo them back for both {@code ask} and {@code stream} paths.
 * The stream path emits a single {@code Delta} followed by {@code Done}.
 */
public final class MockLlmProvider implements LlmProvider {

    private final String tag;
    private final Map<String, String> replies = new HashMap<>();
    private String fallback = "mock-reply";

    public MockLlmProvider() { this("mock"); }
    public MockLlmProvider(String tag) { this.tag = tag; }

    public MockLlmProvider on(String prompt, String reply) {
        replies.put(prompt, reply);
        return this;
    }

    public MockLlmProvider fallback(String reply) {
        this.fallback = reply;
        return this;
    }

    @Override public String tag() { return tag; }

    @Override
    public String ask(EntityValue cfg, String prompt) {
        return replies.getOrDefault(prompt, fallback);
    }

    @Override
    public Value stream(EntityValue cfg, String prompt, FunctionValue onChunk, Interpreter interp) {
        String text = replies.getOrDefault(prompt, fallback);
        CompletableFuture<Value> f = CompletableFuture.supplyAsync(() -> {
            interp.apply(onChunk, Chunks.delta(text));
            interp.apply(onChunk, Chunks.done());
            return UnitValue.INSTANCE;
        });
        return new PromiseValue(f);
    }
}
