package org.twelve.outline.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.interpreter.OutlineInterpreter;
import org.twelve.gcp.interpreter.value.Value;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end run against a real OpenAI-compatible backend (defaults to
 * DeepSeek).  Skipped automatically unless {@code LLM_API_KEY} is set, so
 * CI stays deterministic.
 *
 * <p>Run it locally with:
 * <pre>
 *   export LLM_API_KEY=sk-...
 *   export LLM_BASE_URL=https://api.deepseek.com/v1   # optional, this is the default
 *   export LLM_MODEL=deepseek-chat                    # optional
 *   mvn -pl outline-ai-plugin test -Dtest=DeepSeekLiveIT
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
public class DeepSeekLiveIT {

    private static String model() {
        String m = System.getenv("LLM_MODEL");
        return (m == null || m.isBlank()) ? "deepseek-chat" : m;
    }

    private static Value run(String source) {
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), source);
        ast.asf().infer();
        OutlineInterpreter interp = new OutlineInterpreter();
        AiPlugin.install(interp, DeepSeekAsk.fromEnv());
        return interp.run(ast.asf());
    }

    @Test
    void live_single_call_returns_ok() {
        String src = """
                outline AiResult = Ok{text:String} | Denied{reason:String} | Timeout;
                let r = __llm__("%s", "Reply with exactly the word OK and nothing else.");
                match r { Ok{text} -> text, Denied{reason} -> "denied:" + reason, _ -> "timeout" }
                """.formatted(model());
        Value v = run(src);
        System.out.println("[DeepSeek reply] " + v);
        String s = v.toString();
        assertFalse(s.startsWith("\"denied:"), () -> "LLM call failed: " + s);
        assertFalse(s.equals("\"timeout\""),   () -> "LLM call timed out");
        assertTrue(s.toLowerCase().contains("ok"), () -> "Expected 'OK' in reply, got: " + s);
    }

    @Test
    void live_ai_flow_end_to_end() {
        String src = """
                outline Stream = <a> {
                    data:   [a],
                    filter: (p: a -> Bool) -> this{ data = data.filter(p) },
                    map:    <b>(f: a -> b) -> this{ data = data.map(f) }
                };
                outline AiResult = Ok{text:String} | Denied{reason:String} | Timeout;
                outline AiFlow = <a> Stream<a> {
                    model:    String,
                    ask:      String -> AiResult,
                    prompt:   (p: a -> String) -> this{ data = data.map(p) },
                    complete: () -> this{
                        data = data
                          .map(p -> ask(p))
                          .filter(r -> match r { Ok{text} -> true, _ -> false })
                          .map(r -> match r { Ok{text} -> text, _ -> "" })
                    }
                };

                let ask = p -> __llm__("%s", p);

                AiFlow{ model="%s", ask=ask, data=["cats", "rust"] }
                  .prompt(t -> "Write a single short sentence about " + t + ". Reply with the sentence only.")
                  .complete()
                  .data
                """.formatted(model(), model());
        Value v = run(src);
        System.out.println("[DeepSeek AiFlow] " + v);
        String s = v.toString();
        // Expect a 2-element array of non-empty strings
        assertTrue(s.startsWith("["), s);
        assertTrue(s.length() > 10,  () -> "expected two real replies, got: " + s);
    }

    // ---- One-shot runnable demo: `java ... DeepSeekLiveIT` ----
    public static void main(String[] args) {
        if (System.getenv("LLM_API_KEY") == null) {
            System.err.println("Set LLM_API_KEY first (DeepSeek or any OpenAI-compatible provider).");
            System.exit(1);
        }
        new DeepSeekLiveIT().live_ai_flow_end_to_end();
    }
}
