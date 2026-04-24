package org.twelve.outline.ai.llm;

import org.twelve.gcp.interpreter.Interpreter;
import org.twelve.gcp.interpreter.value.*;
import org.twelve.outline.ai.AiResponse;
import org.twelve.outline.ai.DeepSeekAsk;

import java.util.concurrent.CompletableFuture;

/**
 * OpenAI-compatible provider (OpenAI, DeepSeek, Moonshot, groq, ...).
 *
 * <p>Reads {@code model}, {@code api_key}, {@code base_url} from the per-call
 * EntityValue and delegates to {@link DeepSeekAsk}'s HTTP client.  The stream
 * path currently emits the full response as a single {@code Delta} followed by
 * {@code Done} (true token-level SSE streaming is a future enhancement).
 */
public final class OpenAiProvider implements LlmProvider {

    @Override public String tag() { return "openai"; }

    @Override
    public String ask(EntityValue cfg, String prompt) {
        String model   = stringField(cfg, "model");
        String apiKey  = stringField(cfg, "api_key");
        String baseUrl = stringField(cfg, "base_url");
        DeepSeekAsk client = new DeepSeekAsk(baseUrl, apiKey);
        AiResponse r = client.ask(model, prompt);
        if (r instanceof AiResponse.Ok ok) return ok.text() == null ? "" : ok.text();
        if (r instanceof AiResponse.Denied d) throw new RuntimeException("LLM denied: " + d.reason());
        throw new RuntimeException("LLM timeout");
    }

    @Override
    public Value stream(EntityValue cfg, String prompt, FunctionValue onChunk, Interpreter interp) {
        CompletableFuture<Value> f = CompletableFuture.supplyAsync(() -> {
            try {
                String text = ask(cfg, prompt);
                interp.apply(onChunk, Chunks.delta(text));
                interp.apply(onChunk, Chunks.done());
            } catch (RuntimeException e) {
                interp.apply(onChunk, Chunks.error(e.getMessage()));
            }
            return UnitValue.INSTANCE;
        });
        return new PromiseValue(f);
    }

    private static String stringField(EntityValue cfg, String name) {
        Value v = cfg.get(name);
        if (v instanceof StringValue sv) return sv.value();
        return "";
    }
}
