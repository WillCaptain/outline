package org.twelve.outline.ai.llm;

import org.twelve.gcp.interpreter.OutlineInterpreter;

/**
 * Installer for the {@code __llm__<T>} external-builder and its provider registry.
 *
 * <p>Companion Outline stdlib lives at {@code stdlib/llm.outline} — copy its
 * declarations into your source to use {@code LLM}, {@code OpenAI}, {@code Chunk}
 * and the {@code open_ai(...)} factory.  Auto-loading is coming (see AiFlow
 * refactor in the roadmap).
 *
 * <pre>
 *   OutlineInterpreter interp = new OutlineInterpreter();
 *   LlmEntityPlugin.install(interp, new OpenAiProvider());
 *   // user code: let llm = open_ai("deepseek-chat", env("LLM_API_KEY"), env_or("LLM_BASE_URL", "..."));
 *   //            let a = llm.ask("hi");
 * </pre>
 */
public final class LlmEntityPlugin {

    public static final String ID = "llm";

    private LlmEntityPlugin() {}

    /** Registers {@code __llm__<T>} with the given providers. */
    public static OutlineInterpreter install(OutlineInterpreter interp, LlmProvider... providers) {
        LlmConstructor ctor = new LlmConstructor(interp);
        for (LlmProvider p : providers) ctor.register(p);
        interp.registerConstructor(ID, ctor);
        return interp;
    }
}
