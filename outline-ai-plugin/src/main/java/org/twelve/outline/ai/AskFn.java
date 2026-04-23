package org.twelve.outline.ai;

/**
 * Single-arrow abstraction over an LLM client.
 *
 * <p>Implementations are called from Outline code via the {@code __llm__}
 * host-plugin bridge:
 *
 * <pre>
 *   let ask = prompt -&gt; __llm__("gpt-5", prompt);
 * </pre>
 *
 * <p>The host is responsible for transport, retry, auth and decoding.
 * Failures must be encoded as a {@link AiResponse} variant rather than
 * thrown, so that Outline code can match cleanly:
 *
 * <pre>
 *   match ask("hi") {
 *     Ok{text}         -&gt; ...,
 *     Denied{reason}   -&gt; ...,
 *     Timeout          -&gt; ...
 *   }
 * </pre>
 */
@FunctionalInterface
public interface AskFn {
    AiResponse ask(String model, String prompt);
}
