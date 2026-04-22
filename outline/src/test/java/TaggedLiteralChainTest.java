import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for method/member chaining directly on a tagged-entity literal
 * such as {@code Stream{data=[1,2,3]}.filter(...)} or {@code Variant.Case{...}.field}.
 *
 * <p>Previously the grammar attached the trailing {@code entity} at {@code complex_expression}
 * level, which forbade further {@code .f()}, {@code (x)}, {@code [i]} chaining because
 * those operators live one level deeper at {@code factor_expression}. The entity trailer
 * has been relocated to {@code factor_expression} as a left-recursive suffix alternative
 * so it composes naturally with the rest of the chain.
 */
class TaggedLiteralChainTest {

    private static AST parse(String code) {
        AST ast = new OutlineParser().parse(code);
        ast.asf().infer();
        return ast;
    }

    @Test
    void tagged_literal_chains_member_access() {
        AST ast = parse("""
                outline S = A{n:Int};
                let r = A{n=1}.n;
                """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
        assertEquals(2, ast.program().body().nodes().size());
    }

    @Test
    void tagged_literal_chains_method_call() {
        AST ast = parse("""
                outline Box = {v:Int, inc:Int->Int};
                let r = Box{v=1, inc = x-> x+1}.inc(10);
                """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }

    @Test
    void stream_literal_full_chain_succeeds() {
        AST ast = parse("""
                outline Stream = <a> {
                  data: [a],
                  filter: (p: a -> Bool) -> this{ data = data.filter(p) },
                  map:    <b>(f: a -> b) -> this{ data = data.map(f) }
                };
                let r = Stream{ data = [1,2,3,4,5] }
                        .filter(x -> x > 1)
                        .map(x -> x * x)
                        .data;
                """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }

    @Test
    void qualified_ctor_chains_field_access() {
        AST ast = parse("""
                outline Status = Pending{name:String}|Approved{boss:String};
                let who = Status.Approved{boss="will"}.boss;
                """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }

    @Test
    void bare_tagged_literal_still_works() {
        AST ast = parse("""
                outline S = <a>{data:[a]};
                let s = S{data=[1,2,3]};
                """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
        assertEquals(2, ast.program().body().nodes().size());
    }

    @Test
    void record_extension_on_identifier_still_works() {
        AST ast = parse("""
                let p = {name="x", age=10};
                let q = p{age=11};
                """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
        assertEquals(2, ast.program().body().nodes().size());
    }
}
