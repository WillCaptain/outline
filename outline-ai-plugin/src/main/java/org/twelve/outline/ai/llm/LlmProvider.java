package org.twelve.outline.ai.llm;

import org.twelve.gcp.interpreter.Interpreter;
import org.twelve.gcp.interpreter.value.EntityValue;
import org.twelve.gcp.interpreter.value.FunctionValue;
import org.twelve.gcp.interpreter.value.Value;

/**
 * Host-side adapter for a specific LLM provider (OpenAI, Anthropic, DeepSeek mock, ...).
 *
 * <p>Each provider is keyed by a literal tag that Outline users spell as
 * {@code provider: #"openai"} inside the {@code outline OpenAI = LLM{...}}
 * declaration.  The {@link LlmConstructor} dispatches {@code __llm__<T>} to
 * the provider whose {@link #tag()} matches {@code T}'s provider literal.
 */
public interface LlmProvider {

    /** The literal value of the {@code provider} field on the provider's Outline type. */
    String tag();

    /**
     * Synchronous call.  Receives the fully-configured LLM EntityValue
     * ({@code model}, {@code api_key}, {@code base_url}, ...) plus the user prompt.
     * Returns the answer text.  Throws on failure.
     */
    String ask(EntityValue cfg, String prompt);

    /**
     * Streaming call.  Invokes {@code onChunk} on each {@code Chunk} EntityValue
     * ({@code Delta{text}}, {@code Done}, {@code Error{reason}}) and returns a
     * {@code Promise<Unit>} that completes when the stream ends.
     */
    Value stream(EntityValue cfg, String prompt, FunctionValue onChunk, Interpreter interp);
}
