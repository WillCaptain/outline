package org.twelve.outline.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic {@link AskFn} used by tests, tutorials and the playground.
 *
 * <p>Answers are looked up by exact {@code prompt} key (optionally prefixed by
 * {@code model|}).  Unknown prompts default to {@link AiResponse.Timeout} so
 * that the Outline side can exercise its failure branches.
 */
public final class MockAiPlugin implements AskFn {

    private final Map<String, AiResponse> fixtures = new LinkedHashMap<>();
    private AiResponse fallback = AiResponse.Timeout.INSTANCE;

    /** Register a direct prompt → response mapping. */
    public MockAiPlugin on(String prompt, AiResponse response) {
        fixtures.put(prompt, response);
        return this;
    }

    /** Shortcut: reply with {@code text} when {@code prompt} is asked. */
    public MockAiPlugin onAsk(String prompt, String text) {
        return on(prompt, new AiResponse.Ok(text));
    }

    /** Register a model-qualified mapping (key = {@code model|prompt}). */
    public MockAiPlugin on(String model, String prompt, AiResponse response) {
        fixtures.put(model + "|" + prompt, response);
        return this;
    }

    /** Override the default response returned when no fixture matches. */
    public MockAiPlugin fallback(AiResponse response) {
        this.fallback = response;
        return this;
    }

    @Override
    public AiResponse ask(String model, String prompt) {
        AiResponse direct = fixtures.get(prompt);
        if (direct != null) return direct;
        AiResponse keyed = fixtures.get(model + "|" + prompt);
        if (keyed != null) return keyed;
        return fallback;
    }
}
