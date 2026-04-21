import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the relaxed single-arg lambda form that allows a non-function type
 * annotation without parentheses:
 *
 * <pre>
 *   x          -> body          // bare, unchanged
 *   x:Integer  -> body          // NEW
 *   x:{..}     -> body          // NEW (entity_type)
 *   x:[Integer]-> body          // NEW (array_type)
 *   x:(A,B)    -> body          // NEW (tuple_type)
 *   x:A|B      -> body          // NEW (adt_type)
 *   x:(A->B)   -> body          // NEW (parenthesised func type is non_func_type)
 *   (x:A->B)   -> body          // still valid via the () form
 *   x:A->B     -> body          // rejected: ambiguous; user must parenthesise
 * </pre>
 *
 * The grammar change is purposely limited to {@code non_func_type} so that
 * the bare-form rule introduces no ambiguity with the trailing {@code '->'}
 * of {@code lambda}. Function-typed arguments still require a paren pair
 * around either the argument or the type, which matches TypeScript's
 * {@code (x: (n: number) => boolean) => ...} convention.
 *
 * <p>These tests focus on <em>parse + basic inference success</em> for the
 * new bare form; deeper type-system behaviour (projection, ADT coercion,
 * array accessors) is covered elsewhere.
 */
public class BareTypedLambdaArgTest {

    private static final OutlineParser parser = new OutlineParser();

    private static AST parseAndInfer(String code) {
        AST ast = parser.parse(new ASF(), code);
        ast.asf().infer();
        return ast;
    }

    @Test
    void bare_single_arg_with_primitive_type() {
        AST ast = parseAndInfer("let id = x:Integer -> x; let r = id(3);");
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void bare_single_arg_with_entity_type() {
        AST ast = parseAndInfer("let age_of = p:{age:Integer} -> p.age; let r = age_of({age = 10});");
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void bare_single_arg_with_string_type() {
        AST ast = parseAndInfer("let id = s:String -> s; let r = id(\"hi\");");
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void bare_single_arg_with_parenthesised_func_type_parses() {
        // Parser-level check: `f:(Integer->Integer) -> f(3)` must at least
        // round-trip through parser + builder without a grammar error. Deeper
        // inference equivalences with the `(f:Integer->Integer) -> …` form
        // are covered by the type-system test suite.
        AST ast = parser.parse(new ASF(), "let apply3 = f:(Integer->Integer) -> f(3);");
        assertTrue(ast.errors().isEmpty(),
                "unexpected parse errors: " + ast.errors());
    }

    @Test
    void paren_form_with_func_type_still_parses() {
        AST ast = parser.parse(new ASF(), "let apply3 = (f:Integer->Integer) -> f(3);");
        assertTrue(ast.errors().isEmpty(),
                "unexpected parse errors: " + ast.errors());
    }

    @Test
    void untyped_bare_single_arg_still_works() {
        AST ast = parseAndInfer("let id = x -> x; let r = id(7);");
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void bare_func_type_without_parens_is_rejected() {
        // `x:Integer->Bool -> body` is deliberately ambiguous: the first
        // `->` could belong to either the type annotation or the lambda
        // arrow. The grammar resolves this by requiring the user to
        // parenthesise either the argument or the type. Rejection surfaces
        // as a GrammarSyntaxException during parsing.
        boolean rejected;
        try {
            AST ast = parser.parse(new ASF(), "let bad = x:Integer->Boolean -> x;");
            rejected = !ast.errors().isEmpty();
        } catch (RuntimeException e) {
            rejected = true;
        }
        assertTrue(rejected,
                "expected the unparenthesised func-type annotation to be rejected");
    }

    @Test
    void paren_multi_arg_func_type_is_unambiguous() {
        // Inside '(...)', `y:String->Integer` is unambiguous: `)` and `,`
        // cannot appear inside a type, so `declared_outline` greedily
        // consumes `String->Integer`, and the trailing `->` is the lambda
        // arrow. Equivalent to `y : (String->Integer)`.
        AST a = parser.parse(new ASF(),
                "let h = (x:String, y:String->Integer) -> y(x);");
        AST b = parser.parse(new ASF(),
                "let h = (x:String, y:(String->Integer)) -> y(x);");
        assertTrue(a.errors().isEmpty(), "unexpected errors in form A: " + a.errors());
        assertTrue(b.errors().isEmpty(), "unexpected errors in form B: " + b.errors());
    }

    @Test
    void type_annotation_constrains_call_site() {
        // The annotation must actually propagate to the call site.
        AST ast = parseAndInfer("let id = x:Integer -> x; let bad = id(\"hi\");");
        assertFalse(ast.errors().isEmpty(),
                "expected a type error when passing String to x:Integer argument");
    }
}
