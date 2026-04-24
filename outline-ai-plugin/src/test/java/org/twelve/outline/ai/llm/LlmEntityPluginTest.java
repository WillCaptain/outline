package org.twelve.outline.ai.llm;

import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.interpreter.OutlineInterpreter;
import org.twelve.gcp.interpreter.value.*;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the new atomic LLM capability built on the {@code __llm__<T>}
 * external-builder and {@code this}-aware builtin methods.
 *
 * <p>The tests use a {@link MockLlmProvider} (tag = "mock") so they run
 * offline and deterministically.  The Outline source mirrors exactly what
 * stdlib/llm.outline ships — so regressions in that file are caught here.
 */
public class LlmEntityPluginTest {

    // Stdlib-equivalent prelude.  Uses "mock" provider tag so we can route
    // to MockLlmProvider without hitting the network.
    private static final String PRELUDE = """
            outline Chunk = Delta{text:String} | Done | Error{reason:String};

            outline LLM = {
                ask:    String -> String,
                stream: (String, Chunk -> Unit) -> Promise<Unit>
            };

            outline MockLLM = LLM{
                provider: #"mock",
                model:    String,
                api_key:  String,
                base_url: String
            };

            let mock_llm = (model, api_key, base_url) ->
                __llm__<MockLLM>{
                    model    = model,
                    api_key  = api_key,
                    base_url = base_url
                };
            """;

    private Value run(String body, LlmProvider... providers) {
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), PRELUDE + body);
        ast.asf().infer();
        OutlineInterpreter interp = new OutlineInterpreter();
        LlmEntityPlugin.install(interp, providers);
        return interp.run(ast.asf());
    }

    // ── core: sync ask returns provider output ──────────────────────────────
    @Test
    void ask_returns_provider_answer() {
        MockLlmProvider mock = new MockLlmProvider().on("hi", "hello, world");
        String src = """
                let llm = mock_llm("m", "k", "u");
                llm.ask("hi")
                """;
        Value v = run(src, mock);
        assertInstanceOf(StringValue.class, v);
        assertEquals("hello, world", ((StringValue) v).value());
    }

    // ── core: fallback when prompt unmapped ─────────────────────────────────
    @Test
    void ask_fallback_is_default_reply() {
        MockLlmProvider mock = new MockLlmProvider().fallback("whatever");
        String src = """
                let llm = mock_llm("m", "k", "u");
                llm.ask("unknown prompt")
                """;
        assertEquals("whatever", ((StringValue) run(src, mock)).value());
    }

    // ── this binding: different configs route to same provider but carry
    //    their own model/api_key visible to the provider adapter ────────────
    @Test
    void this_aware_methods_see_per_instance_config() {
        LlmProvider echoConfig = new LlmProvider() {
            @Override public String tag() { return "mock"; }
            @Override
            public String ask(EntityValue cfg, String prompt) {
                return ((StringValue) cfg.get("model")).value() + "|" +
                       ((StringValue) cfg.get("api_key")).value() + "|" + prompt;
            }
            @Override
            public Value stream(EntityValue cfg, String prompt,
                                 FunctionValue onChunk,
                                 org.twelve.gcp.interpreter.Interpreter interp) {
                return UnitValue.INSTANCE;
            }
        };
        String src = """
                let a = mock_llm("gpt-5",      "KA", "u1");
                let b = mock_llm("deepseek-r1", "KB", "u2");
                (a.ask("q1"), b.ask("q2"))
                """;
        Value v = run(src, echoConfig);
        assertEquals("(\"gpt-5|KA|q1\",\"deepseek-r1|KB|q2\")", v.toString());
    }

    // ── provider dispatch by `provider` literal tag ─────────────────────────
    @Test
    void provider_dispatch_by_literal_tag() {
        String src = """
                outline FakeA = LLM{
                    provider: #"alpha",
                    model:    String
                };
                outline FakeB = LLM{
                    provider: #"beta",
                    model:    String
                };
                let a = __llm__<FakeA>{ model = "ma" };
                let b = __llm__<FakeB>{ model = "mb" };
                (a.ask("x"), b.ask("x"))
                """;
        MockLlmProvider alpha = new MockLlmProvider("alpha").fallback("A!");
        MockLlmProvider beta  = new MockLlmProvider("beta").fallback("B!");
        Value v = run(src, alpha, beta);
        assertEquals("(\"A!\",\"B!\")", v.toString());
    }

    // ── unknown provider tag produces a clear runtime error ─────────────────
    @Test
    void unregistered_provider_errors_with_useful_message() {
        String src = """
                let llm = mock_llm("m", "k", "u");
                llm.ask("hi")
                """;
        // No providers installed for "mock"
        RuntimeException ex = assertThrows(RuntimeException.class, () -> run(src));
        assertTrue(ex.getMessage().contains("mock"),
                "Expected provider tag in message, got: " + ex.getMessage());
    }

    // ── extensibility: users stack their own operators on top of LLM
    //    without the host plugin knowing anything about them ──────────────
    @Test
    void user_wraps_llm_with_custom_operator() {
        MockLlmProvider mock = new MockLlmProvider().on("Hi!", "Hello there");
        String src = """
                let llm   = mock_llm("m", "k", "u");
                let shout = (l, p) -> l.ask(p + "!");
                shout(llm, "Hi")
                """;
        Value v = run(src, mock);
        assertEquals("Hello there", ((StringValue) v).value());
    }
}
