import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.exception.GCPErrCode;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the {@code fn name<generics>(args):R { body }} declaration form.
 *
 * <p>{@code fn} is pure syntactic sugar for
 * {@code let name = <generics>(args):R -> { body };} — these tests therefore
 * focus on the desugaring being behaviour-equivalent to the hand-written
 * {@code let}+lambda form: callsite type inference, generic instantiation,
 * recursion and return-type mismatch all work the same way.
 *
 * <p>Slice 2 consistency checks (both LHS {@code let f:F} and inline {@code
 * :R} on the same function; named self-reference pre-binding beyond what
 * {@code IdentifierInference.confirmRecursive} already supplies) are out of
 * scope here and will be added in a follow-up.
 */
public class FnDeclaratorTest {

    private static final OutlineParser parser = new OutlineParser();

    private static AST parseAndInfer(String code) {
        AST ast = parser.parse(new ASF(), code);
        ast.asf().infer();
        return ast;
    }

    @Test
    void simple_fn_no_annotations() {
        AST ast = parseAndInfer("fn id(x:Int) { x } let r = id(3);");
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void fn_with_declared_return_type() {
        AST ast = parseAndInfer("fn twice(x:Int):Int { x+x } let r = twice(5);");
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void fn_return_type_mismatch_reports_outline_mismatch() {
        AST ast = parseAndInfer("fn bad(x:Int):Int { \"oops\" }");
        assertTrue(
            ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.OUTLINE_MISMATCH),
            "expected OUTLINE_MISMATCH, got: " + ast.errors()
        );
    }

    @Test
    void fn_generic_with_instantiation() {
        AST ast = parseAndInfer(
            "fn pick<a>(x:a, y:a):a { x+y }" +
            "let z1 = pick<String>;" +
            "let z2 = pick<Int>;" +
            "let a = z1(\"w\", \"z\");" +
            "let b = z2(1, 2);"
        );
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void fn_recursive_self_reference() {
        AST ast = parseAndInfer(
            "fn fact(n:Int):Int { if(n<=1) 1 else n*fact(n-1) }" +
            "let r = fact(5);"
        );
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }

    @Test
    void fn_semicolon_optional() {
        AST ast = parseAndInfer(
            "fn a(x:Int) { x };" +
            "fn b(x:Int) { x }"
        );
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
    }
}
