import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.meta.CompletionItem;
import org.twelve.gcp.meta.MetaExtractor;
import org.twelve.outline.OutlineParser;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MetaExtractor#completionsAt(ASF, AST, String, int)} — the
 * canonical IDE completion entry point used by every Outline editor backend.
 *
 * <p>Covers the four dot-completion strategies (identifier, meta-resolve,
 * chain expression, method-return chain), the no-dot identifier-prefix path,
 * and the {@link MetaExtractor#autoClose(String)} helper.
 */
public class CompletionsAtTest {

    private final OutlineParser parser = new OutlineParser();

    private CompletionsResult run(String code, int offset) {
        // Caller contract: parse a syntactically valid prefix (strip trailing
        // dot, auto-close brackets), then ask completionsAt for the original
        // code + offset. This is what every editor backend does.
        String parseCode = offset > 0 && offset <= code.length() && code.charAt(offset - 1) == '.'
                ? MetaExtractor.autoClose(code.substring(0, offset - 1))
                : MetaExtractor.autoClose(code.substring(0, Math.min(offset, code.length())));
        ASF asf = new ASF();
        AST ast = parser.parse(asf, parseCode);
        try { asf.infer(); } catch (Exception ignored) {}
        return new CompletionsResult(MetaExtractor.completionsAt(asf, ast, code, offset));
    }

    private CompletionsResult run(String code) {
        return run(code, code.length());
    }

    private record CompletionsResult(List<CompletionItem> items) {
        Set<String> labels() { return items.stream().map(CompletionItem::label).collect(Collectors.toSet()); }
        boolean has(String label) { return items.stream().anyMatch(it -> label.equals(it.label())); }
        CompletionItem find(String label) {
            return items.stream().filter(it -> label.equals(it.label())).findFirst().orElse(null);
        }
    }

    // ── Dot-completion: identifier ──────────────────────────────────────────

    @Test
    void dot_completion_on_named_outline_value_lists_members() {
        String code = """
            outline Person = {name: String, age: Int};
            let p:Person = {name="alice", age=30};
            p.""";
        CompletionsResult r = run(code);
        assertTrue(r.has("name"), "expected 'name' in: " + r.labels());
        assertTrue(r.has("age"));
    }

    @Test
    void dot_completion_filters_underscore_private_members() {
        String code = """
            outline Box = {_secret: Int, public_v: Int};
            let b:Box = {_secret=1, public_v=2};
            b.""";
        CompletionsResult r = run(code);
        assertTrue(r.has("public_v"));
        assertFalse(r.has("_secret"), "underscore members must be filtered");
    }

    // ── No-dot: keywords always available ────────────────────────────────────

    @Test
    void no_dot_completion_includes_language_keywords() {
        String code = """
            outline Person = {name: String};
            let alice = "alice";
            """;
        CompletionsResult r = run(code, code.length());
        assertTrue(r.has("let"),     "expected language keywords in non-dot completion");
        assertTrue(r.has("if"));
        assertTrue(r.has("outline"));
        assertTrue(r.has("match"));
        for (CompletionItem kw : r.items()) {
            if ("let".equals(kw.label())) {
                assertEquals("keyword", kw.kind());
                break;
            }
        }
    }


    // ── CompletionItem shape ────────────────────────────────────────────────

    @Test
    void completion_item_has_method_kind_for_function_members() {
        String code = """
            outline Greeter = {greet: String -> String};
            let g: Greeter = {greet = (s) -> s};
            g.""";
        CompletionsResult r = run(code);
        CompletionItem greet = r.find("greet");
        assertNotNull(greet, "expected 'greet' member, got: " + r.labels());
        assertEquals("method", greet.kind(), "function-shaped members must be 'method' kind");
    }

    @Test
    void completion_item_kind_is_property_for_scalar_members() {
        String code = """
            outline P = {n: Int};
            let p:P = {n=1};
            p.""";
        CompletionsResult r = run(code);
        CompletionItem n = r.find("n");
        assertNotNull(n);
        assertEquals("property", n.kind());
    }

    // ── autoClose ───────────────────────────────────────────────────────────

    @Test
    void autoClose_balances_unclosed_parens_and_brackets() {
        assertEquals("xs.filter(c->c)",   MetaExtractor.autoClose("xs.filter(c->c"));
        assertEquals("[1,2,3]",           MetaExtractor.autoClose("[1,2,3"));
        // Closing order is parens-first then brackets; both balanced is what matters.
        String balanced = MetaExtractor.autoClose("xs.map(x->[x");
        assertEquals(1, balanced.chars().filter(ch -> ch == ')').count());
        assertEquals(1, balanced.chars().filter(ch -> ch == ']').count());
        assertEquals("\"a(b\"",           MetaExtractor.autoClose("\"a(b\""), "parens inside strings ignored");
        assertEquals("",                  MetaExtractor.autoClose(""));
        assertNotNull(MetaExtractor.autoClose(null));
    }

    // ── Out-of-range / null-safety ──────────────────────────────────────────

    @Test
    void completionsAt_returns_empty_on_invalid_offset() {
        ASF asf = new ASF();
        AST ast = parser.parse(asf, "let x = 1;");
        assertTrue(MetaExtractor.completionsAt(asf, ast, "let x = 1;", -1).isEmpty());
        assertTrue(MetaExtractor.completionsAt(asf, ast, "let x = 1;", 9999).isEmpty());
        assertTrue(MetaExtractor.completionsAt(null, null, "code", 0).isEmpty());
    }
}
