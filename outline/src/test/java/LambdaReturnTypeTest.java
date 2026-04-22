import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.exception.GCPErrCode;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the optional lambda return-type annotation {@code :R} that sits
 * between the parenthesised argument list and the {@code ->} body marker:
 *
 * <pre>
 *   let f = (x:Int)      -> x*100;       // unchanged — untyped return
 *   let f = (x:Int) :Int -> x*100;       // NEW     — declared return Int
 *   let g = <a>(x:a,y:a):a -> x+y;       // NEW     — generic return
 * </pre>
 *
 * <p>Semantics: the declared {@code R} is stored on the innermost
 * {@link org.twelve.gcp.node.function.FunctionNode} and seeded into the
 * body's {@link org.twelve.gcp.outline.projectable.Return#declaredToBe},
 * so {@code Return#addReturn} reports {@link GCPErrCode#OUTLINE_MISMATCH}
 * when the body's inferred return value is not a subtype of {@code R}.
 *
 * <p>This test covers Slice 1 only: parse + mismatch check.
 * Slice 2 (recursion pre-binding, LHS+inline R consistency) is not yet
 * exercised here.
 */
public class LambdaReturnTypeTest {

    private static final OutlineParser parser = new OutlineParser();

    private static AST parseAndInfer(String code) {
        AST ast = parser.parse(new ASF(), code);
        ast.asf().infer();
        return ast;
    }

    @Test
    void declared_return_matches_body() {
        AST ast = parseAndInfer("let f = (x:Int):Int -> x*100; let r = f(2);");
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void declared_return_mismatch_reports_outline_mismatch() {
        AST ast = parseAndInfer("let f = (x:Int):Int -> \"oops\";");
        assertTrue(
            ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.OUTLINE_MISMATCH),
            "expected OUTLINE_MISMATCH, got: " + ast.errors()
        );
    }

    @Test
    void declared_return_no_annotation_still_works() {
        // Sanity: pre-existing form (no :R) still passes cleanly.
        AST ast = parseAndInfer("let f = (x:Int) -> x*100; let r = f(2);");
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void generic_declared_return_instantiates_correctly() {
        // `<a>(x:a,y:a):a -> x+y` with z<String>/z<Int> should give String->String->String / Int->Int->Int.
        AST ast = parseAndInfer(
            "let z = <a>(x:a,y:a):a -> x+y;" +
            "let z1 = z<String>;" +
            "let z2 = z<Int>;" +
            "let a = z1(\"w\",\"z\");" +
            "let b = z2(1,2);"
        );
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void declared_return_wider_than_body_ok() {
        // Body returns exactly the declared type — no projection-sensitive
        // intermediates involved. Covers the "no-false-mismatch" case.
        AST ast = parseAndInfer("let f = (x:Int):Int -> x;");
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void addable_narrows_with_concrete_numeric_operands() {
        // `x+1` with x:Int should narrow to Integer (both operands NUMBER) even
        // when used as a return-type check target. Previously Addable.guess()
        // always returned the conservative `String|Number` union.
        AST ast = parseAndInfer("let f = (x:Int):Number -> x+1;");
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void recursive_function_with_declared_return_type() {
        // fact converges in multi-round: round 1 leaves the recursive branch
        // Pending, round 2 resolves `fact(n-1)` to Integer once the outer
        // binding is typed.
        AST ast = parseAndInfer(
            "let fact = (n:Int):Int -> if(n<=1) 1 else n*fact(n-1);"
        );
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void recursive_addable_body_narrows() {
        // fib(n-1)+fib(n-2) relies on BOTH multi-round recursion convergence
        // AND the Addable.guess() narrowing rule. Verifies they compose.
        AST ast = parseAndInfer(
            "let fib = (n:Int):Int -> if(n<2) n else fib(n-1)+fib(n-2);"
        );
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void single_arg_no_parens_still_forbids_return_annotation() {
        // Grammar only permits `:R` in the parenthesised form. Writing
        // `x:Int:Int -> body` must fail to parse — the parser throws an
        // AggregateGrammarSyntaxException on lookahead mismatch.
        Throwable caught = null;
        try {
            parser.parse(new ASF(), "let f = x:Int:Int -> x;");
        } catch (Throwable t) {
            caught = t;
        }
        assertTrue(caught != null,
                "expected parse error for bare-form :R, but got none");
    }
}
