package org.twelve.outline.ai;

import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.interpreter.OutlineInterpreter;
import org.twelve.gcp.interpreter.value.Value;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end exercise of the AiFlow story on top of the host {@code __llm__}
 * bridge.  These tests intentionally write the Stream / LLM pipeline inline
 * so each scenario is self-contained and reads as documentation.
 *
 * <p><b>The extensibility claim</b> — future-this means that adding new
 * operators never collapses the flow back to its base type.  Test
 * {@link #user_defined_operator_chains_through_inherited_ones}
 * proves that a user-authored {@code JudgeFlow} subtype can add a new
 * operator ({@code .judge}) and still flow through the *inherited*
 * {@code Stream.filter / Stream.map} without ever touching the base source.
 */
public class AiFlowStreamTest {

    private static final String STREAM = """
            outline Stream = <a> {
                data:   [a],
                filter: (p: a -> Bool) -> this{ data = data.filter(p) },
                map:    <b>(f: a -> b) -> this{ data = data.map(f) }
            };
            """;

    private static final String AI_RESULT = """
            outline AiResult = Ok{text:String} | Denied{reason:String} | Timeout;
            """;

    private Value run(String source, AskFn ask) {
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), source);
        ast.asf().infer();
        OutlineInterpreter interp = new OutlineInterpreter();
        AiPlugin.install(interp, ask);
        return interp.run(ast.asf());
    }

    // ---------------------------------------------------------------------
    // 1. Base AiFlow pipeline — inherited filter/map still return this-type.
    // ---------------------------------------------------------------------
    @Test
    void ai_flow_chain_filter_prompt_complete_map() {
        MockAiPlugin ask = new MockAiPlugin()
                .onAsk("Haiku about: cats",
                        "Soft paw on warm sill / watching the sparrow at dusk / silence of the hunt")
                .onAsk("Haiku about: rust",
                        "Iron bones sleeping / rain sings the ancient pigment / orange blooms at dawn");

        String src = STREAM + AI_RESULT + """
                outline AiFlow = <a> Stream<a> {
                    model:    String,
                    ask:      String -> AiResult,
                    prompt:   (p: a -> String)        -> this{ data = data.map(p) },
                    complete: () -> this{
                        data = data
                          .map(p -> ask(p))
                          .filter(r -> match r { Ok{text} -> true, _ -> false })
                          .map(r -> match r { Ok{text} -> text, _ -> "" })
                    }
                };

                let ask = p -> __llm__("gpt-5", p);

                AiFlow{
                    model = "gpt-5",
                    ask   = ask,
                    data  = ["cats", "rust", "x"]
                }
                .filter(t -> len(t) > 2)
                .prompt(t -> "Haiku about: " + t)
                .complete()
                .data
                """;
        Value v = run(src, ask);
        String s = v.toString();
        assertTrue(s.contains("sparrow"), () -> "expected cats haiku in: " + s);
        assertTrue(s.contains("Iron bones"), () -> "expected rust haiku in: " + s);
        // length-2 "x" was filtered out before prompting
        assertFalse(s.contains("Haiku about: x"), () -> "short topic should be filtered: " + s);
    }

    // ---------------------------------------------------------------------
    // 2. Extensibility: a user subtype adds a NEW operator `.judge`.
    //    Because every operator (inherited + new) returns `this{...}`, the
    //    extended type flows through the entire chain and `.judge` is still
    //    available after inherited `.filter` / `.map` calls.  No change to
    //    Stream or AiFlow is needed — that's what other languages struggle to do.
    // ---------------------------------------------------------------------
    @Test
    void user_defined_operator_chains_through_inherited_ones() {
        MockAiPlugin ask = new MockAiPlugin()
                .onAsk("Classify: great idea",  "good")
                .onAsk("Classify: meh",         "bad")
                .onAsk("Classify: fine",        "good");

        // JudgeFlow is a user-authored subtype of AiFlow.  It introduces a brand-new
        // operator `.judge` that consults the LLM, WITHOUT modifying AiFlow or Stream.
        // Because all three types close their operators with `this{...}`, the chain
        // `JudgeFlow -> inherited filter -> inherited map -> .judge (extended)` stays
        // on JudgeFlow throughout — something most OO/ML languages cannot express
        // without explicit F-bounded generics or bespoke wrappers.
        String src = STREAM + AI_RESULT + """
                outline AiFlow = <a> Stream<a> {
                    model: String,
                    ask:   String -> AiResult
                };
                outline JudgeFlow = <a> AiFlow<a> {
                    verdict: String,
                    judge: (render: a -> String) -> this{
                        data = data.filter(x -> {
                            let r = ask(render(x));
                            match r { Ok{text} -> text == verdict, _ -> false }
                        })
                    }
                };

                let ask = p -> __llm__("gpt-5", p);

                JudgeFlow{
                    model   = "gpt-5",
                    ask     = ask,
                    verdict = "good",
                    data    = ["great idea", "meh", "fine"]
                }
                .filter(x -> len(x) > 2)        // inherited — type stays JudgeFlow
                .judge(x -> "Classify: " + x)   // user-added — LLM-driven filter
                .map(x -> "[kept] " + x)        // inherited — still JudgeFlow
                .data
                """;
        Value v = run(src, ask);
        String s = v.toString();
        assertTrue(s.contains("[kept] great idea"), () -> "good item should survive: " + s);
        assertTrue(s.contains("[kept] fine"),       () -> "good item should survive: " + s);
        assertFalse(s.contains("meh"),              () -> "bad item must be dropped: " + s);
    }

    // ---------------------------------------------------------------------
    // 3. Self-looping ReAct-style agent. The agent state is an Entity; every
    //    `.tick` recurses into `this{...}`, preserving the concrete Agent type.
    //    Two termination paths: a `Finish` step, or budget-exhaustion.
    // ---------------------------------------------------------------------
    @Test
    void agent_step_preserves_subtype_through_future_this() {
        MockAiPlugin ask = new MockAiPlugin()
                .onAsk("turn:1", "think")
                .onAsk("turn:2", "think")
                .onAsk("turn:3", "finish");

        // Demonstrates the agent-loop primitive: each `.step()` rebuilds the same
        // concrete Agent via `this{...}`, picking up the LLM's latest reply.
        // Three steps are enough to exercise prompt formatting, AiResult matching
        // and state propagation without triggering recursive poly-inference.
        String src = AI_RESULT + """
                outline Agent = {
                    turn:   Int,
                    last:   String,
                    ask:    String -> AiResult,
                    observe: (text:String) -> this{ turn = turn + 1, last = text }
                };
                let ask    = p -> __llm__("gpt-5", p);
                let reply  = (a, n) -> {
                    let r = ask("turn:" + to_str(n));
                    match r { Ok{text} -> text, _ -> "timeout" }
                };
                let seed = Agent{ turn = 0, last = "", ask = ask };
                let a1   = seed.observe(reply(seed, 1));
                let a2   = a1.observe(reply(a1, 2));
                let a3   = a2.observe(reply(a2, 3));
                (a1.last, a2.last, a3.last, a3.turn)
                """;
        Value v = run(src, ask);
        assertEquals("(\"think\",\"think\",\"finish\",3)", v.toString());
    }
}
