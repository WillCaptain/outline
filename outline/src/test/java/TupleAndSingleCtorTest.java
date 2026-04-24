import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for two grammar relaxations:
 *   1. Single-element tuples written as `(T,)` (Python/Rust-style trailing comma).
 *      Bare `(T)` remains grouping, NOT a tuple.
 *   2. Single-constructor ADTs like `outline Gender = Male(String, Int);` (without
 *      a `| Female...` alternative).
 */
public class TupleAndSingleCtorTest {

    private static final OutlineParser parser = ASTHelper.parser;

    private static AST parse(String code) {
        return parser.parse(new ASF(), code);
    }

    // ─────────────────────────── Problem 1: tuple shape ───────────────────────

    @Test
    void single_element_tuple_with_trailing_comma_parses() {
        AST ast = parse("let t: (String,) = (\"hi\",);");
        assertTrue(ast.errors().isEmpty(),
                "(T,) should parse as single-element tuple; got: " + ast.errors());
        assertTrue(ast.asf().infer(),
                "inference should succeed for single-element tuple");
    }

    @Test
    void multi_element_tuple_still_parses() {
        AST ast = parse("let t: (String, Int) = (\"hi\", 1);");
        assertTrue(ast.errors().isEmpty(),
                "multi-element tuple should still work; got: " + ast.errors());
        assertTrue(ast.asf().infer(), "inference should succeed");
    }

    @Test
    void single_element_paren_without_comma_is_grouping_not_tuple() {
        // `(String)` is grouping of the type, so the RHS `"hi"` (a plain String)
        // should be assignable to it. If it were misparsed as a 1-tuple, typing
        // would fail.
        AST ast = parse("let s: (String) = \"hi\";");
        assertTrue(ast.errors().isEmpty(),
                "(T) should be grouping (equivalent to T); got: " + ast.errors());
        assertTrue(ast.asf().infer(), "inference should succeed");
    }

    // ───────────────────── Problem 2: single-constructor ADT ──────────────────

    @Test
    void single_constructor_tuple_variant_parses() {
        String code = """
                outline Gender = Male(String, Int);
                let g = Male("bob", 30);
                g
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(),
                "single-constructor ADT `Male(String,Int)` should parse; got: " + ast.errors());
        assertTrue(ast.asf().infer(),
                "inference should succeed for single-constructor variant");
    }

    @Test
    void multi_branch_adt_still_parses() {
        // Regression: ensure original `|`-separated ADT still works unchanged.
        String code = """
                outline Gender = Male(String, Int) | Female(String, Int);
                let g = Male("bob", 30);
                g
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(),
                "multi-branch ADT should still work; got: " + ast.errors());
        assertTrue(ast.asf().infer(), "inference should succeed");
    }

    @Test
    void single_constructor_with_single_element_tuple_type() {
        // Combines both fixes: declaration uses the trailing-comma tuple type;
        // the call site uses ordinary function-call syntax `Box(42)`.
        String code = """
                outline Wrap = Box(Int,);
                let w = Box(42);
                w
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(),
                "single-ctor with (T,) tuple type should parse; got: " + ast.errors());
        assertTrue(ast.asf().infer(), "inference should succeed");
    }
}
