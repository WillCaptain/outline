package org.twelve.outline.ai.llm;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.interpreter.OutlineInterpreter;
import org.twelve.gcp.interpreter.value.PromiseValue;
import org.twelve.gcp.interpreter.value.StringValue;
import org.twelve.gcp.interpreter.value.UnitValue;
import org.twelve.gcp.interpreter.value.Value;
import org.twelve.outline.OutlineParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end live test against DeepSeek using worldone's local config
 * ({@code ~/.worldone-config.json}) or {@code LLM_API_KEY}/{@code LLM_BASE_URL}
 * env vars.  Exercises the full atomic LLM capability:
 * <pre>
 *   let llm = __llm__&lt;OpenAI&gt;{model=..., api_key=..., base_url=...};
 *   let a   = llm.ask("...");                  // sync
 *   await llm.stream("...", c -&gt; ...);         // async + Chunk callback
 * </pre>
 *
 * <p>Opt-in: requires {@code -DrunLive=true} to avoid hitting the network
 * in normal builds.
 */
public class LlmEntityDeepSeekLiveIT {

    private static String API_KEY;
    private static String BASE_URL;
    private static String MODEL;

    @BeforeAll
    static void load() {
        assumeTrue(Boolean.getBoolean("runLive"),
                "Skipping live test (pass -DrunLive=true to enable)");

        // Prefer worldone config file; fall back to env vars.
        try {
            Path cfg = Path.of(System.getProperty("user.home"), ".worldone-config.json");
            if (Files.exists(cfg)) {
                String json = Files.readString(cfg);
                API_KEY  = extract(json, "apiKey");
                BASE_URL = extract(json, "baseUrl");
                MODEL    = extract(json, "model");
            }
        } catch (Exception ignored) {}

        if (API_KEY == null || API_KEY.isBlank()) API_KEY  = System.getenv("LLM_API_KEY");
        if (BASE_URL == null || BASE_URL.isBlank()) BASE_URL = System.getenv().getOrDefault(
                "LLM_BASE_URL", "https://api.deepseek.com/v1");
        if (MODEL == null || MODEL.isBlank())      MODEL    = System.getenv().getOrDefault(
                "LLM_MODEL", "deepseek-chat");

        assumeTrue(API_KEY != null && !API_KEY.isBlank(),
                "No DeepSeek API key found in ~/.worldone-config.json or LLM_API_KEY");
    }

    private static String extract(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private Value run(String source) {
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), source);
        ast.asf().infer();
        OutlineInterpreter interp = new OutlineInterpreter();
        LlmEntityPlugin.install(interp, new OpenAiProvider());
        return interp.run(ast.asf());
    }

    private static final String PRELUDE = """
            outline Chunk = Delta{text:String} | Done | Error{reason:String};

            outline LLM = {
                ask:    String -> String,
                stream: (String, Chunk -> Unit) -> Promise<Unit>
            };

            outline OpenAI = LLM{
                provider: #"openai",
                model:    String,
                api_key:  String,
                base_url: String
            };

            let open_ai = (model, api_key, base_url) ->
                __llm__<OpenAI>{
                    model    = model,
                    api_key  = api_key,
                    base_url = base_url
                };
            """;

    // ── sync ────────────────────────────────────────────────────────────────
    @Test
    void ask_returns_real_deepseek_answer() {
        String src = PRELUDE + String.format("""
                let llm = open_ai("%s", "%s", "%s");
                llm.ask("Say 'pong' and nothing else.")
                """, MODEL, API_KEY, BASE_URL);

        Value v = run(src);
        assertInstanceOf(StringValue.class, v);
        String text = ((StringValue) v).value();
        System.out.println("[live] ask reply: " + text);
        assertFalse(text.isBlank(), "DeepSeek should return non-empty content");
        // Loose check — LLMs are stochastic but should contain 'pong' somewhere.
        assertTrue(text.toLowerCase().contains("pong"),
                "expected 'pong' in reply, got: " + text);
    }

    // ── async streaming ─────────────────────────────────────────────────────
    @Test
    void stream_emits_delta_then_done() {
        String src = PRELUDE + String.format("""
                let llm = open_ai("%s", "%s", "%s");
                let buf = {var text = ""};
                let fut = llm.stream("Say 'ok' once.", c -> match c {
                    Delta{text as t}   -> buf.text = buf.text + t,
                    Done               -> buf.text = buf.text + "<DONE>",
                    Error{reason as r} -> buf.text = buf.text + "<ERR:" + r + ">"
                });
                await fut;
                buf.text
                """, MODEL, API_KEY, BASE_URL);

        Value v = run(src);
        assertInstanceOf(StringValue.class, v);
        String text = ((StringValue) v).value();
        System.out.println("[live] stream buf: " + text);
        assertTrue(text.contains("<DONE>"),
                "expected <DONE> marker in buffered output, got: " + text);
        assertFalse(text.replace("<DONE>", "").isBlank(),
                "expected non-empty delta before <DONE>, got: " + text);
    }
}
