package org.twelve.outline.ai;

/**
 * Host-side counterpart of the Outline {@code AiResult} ADT.
 *
 * <pre>
 *   outline AiResult = Ok{text:String} | Denied{reason:String} | Timeout;
 * </pre>
 *
 * Host implementations ({@link AskFn}) return one of {@link Ok}, {@link Denied},
 * {@link Timeout} and the plugin lifts the result into an Outline
 * {@code EntityValue} with a matching symbol tag.  This keeps user-supplied
 * LLM clients completely decoupled from the interpreter runtime types.
 */
public sealed interface AiResponse permits AiResponse.Ok, AiResponse.Denied, AiResponse.Timeout {

    record Ok(String text) implements AiResponse {}

    record Denied(String reason) implements AiResponse {}

    record Timeout() implements AiResponse {
        public static final Timeout INSTANCE = new Timeout();
    }
}
