package org.twelve.outline.ai;

import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.interpreter.OutlineInterpreter;
import org.twelve.gcp.interpreter.value.EntityValue;
import org.twelve.gcp.interpreter.value.StringValue;
import org.twelve.gcp.interpreter.value.TupleValue;
import org.twelve.gcp.interpreter.value.Value;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@code __llm__} bridge end-to-end:
 *   1. Outline identifier {@code __llm__} resolves to a curried FunctionValue
 *      installed by {@link AiPlugin#install}.
 *   2. Passing two String arguments invokes the host {@link AskFn}.
 *   3. The returned {@link AiResponse} is lifted into an {@code AiResult}
 *      entity with the correct symbol tag and is fully matchable from Outline.
 */
public class AiPluginSmokeTest {

    private Value run(String source, AskFn ask) {
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), source);
        ast.asf().infer();
        OutlineInterpreter interp = new OutlineInterpreter();
        AiPlugin.install(interp, ask);
        return interp.run(ast.asf());
    }

    private static final String ADT = """
            outline AiResult = Ok{text:String} | Denied{reason:String} | Timeout;
            """;

    @Test
    void raw_llm_call_returns_ok_entity() {
        MockAiPlugin ask = new MockAiPlugin().onAsk("hi", "hello, world");
        Value v = run(ADT + "__llm__(\"gpt-5\", \"hi\")", ask);
        assertInstanceOf(EntityValue.class, v);
        EntityValue ev = (EntityValue) v;
        assertEquals("Ok", ev.symbolTag());
        assertInstanceOf(StringValue.class, ev.get("text"));
        assertEquals("hello, world", ((StringValue) ev.get("text")).value());
    }

    @Test
    void match_over_ok_denied_timeout() {
        MockAiPlugin ask = new MockAiPlugin()
                .onAsk("hi", "hey")
                .on("nope", new AiResponse.Denied("policy"))
                .on("slow", AiResponse.Timeout.INSTANCE);
        String src = ADT + """
                let probe = p -> {
                    let r = __llm__("gpt-5", p);
                    match r {
                        Ok{text}       -> "ok:"   + text,
                        Denied{reason} -> "deny:" + reason,
                        Timeout        -> "timeout",
                        _              -> "other"
                    }
                };
                (probe("hi"), probe("nope"), probe("slow"))
                """;
        Value v = run(src, ask);
        assertEquals("(\"ok:hey\",\"deny:policy\",\"timeout\")", v.toString());
    }

    @Test
    void plugin_is_curried_and_first_class() {
        MockAiPlugin ask = new MockAiPlugin().onAsk("hi", "bonjour");
        // Capture `__llm__` in a let-binding, prove it can be applied partially
        // (only `model` supplied) and then the rest supplied later.
        String src = ADT + """
                let gpt   = __llm__("gpt-5");
                let reply = gpt("hi");
                match reply { Ok{text} -> text, _ -> "err" }
                """;
        Value v = run(src, ask);
        assertEquals("\"bonjour\"", v.toString());
    }

    @Test
    void default_fallback_is_timeout() {
        Value v = run(ADT + """
                let r = __llm__("gpt-5", "unknown");
                match r {
                    Timeout        -> "t",
                    Ok{text}       -> "o",
                    Denied{reason} -> "d"
                }
                """, new MockAiPlugin());
        assertEquals("\"t\"", v.toString());
    }
}
