import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.twelve.gcp.common.Tool.cast;

/**
 * Regression coverage for reference-projection (`f&lt;T&gt;`) narrowing through
 * {@link org.twelve.gcp.outline.projectable.Addable} return positions.
 *
 * <p>Before the fix, {@code Addable} inherited the default
 * {@code Outline.project(Reference, OutlineWrapper)} which is a no-op, so a
 * body like {@code x + y} in {@code let z = &lt;a&gt;(x:a,y:a)->x+y;} would
 * render the return type as the {@code String|Number} union even after
 * {@code z&lt;String&gt;} / {@code z&lt;Number&gt;} explicitly substituted the
 * type variable. Fix: {@link org.twelve.gcp.outline.projectable.Addable#project
 * (Reference, OutlineWrapper)} recursively projects operands and folds using
 * the same rules as the usage-driven {@code doProject} path.
 */
public class GenericAddProjectionTest {

    private AST parse(String code) {
        AST ast = new OutlineParser().parse(new ASF(), code);
        assertTrue(ast.asf().infer());
        return ast;
    }

    private String outline(AST ast, int stmtIdx) {
        VariableDeclarator v = cast(ast.program().body().statements().get(stmtIdx));
        return v.assignments().getFirst().lhs().outline().toString();
    }

    @Test
    void add_reference_projection_narrows_return_type_to_string() {
        AST ast = parse("""
                let add = <a>(x:a,y:a)->x+y;
                let zS = add<String>;
                """);
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
        assertEquals("String->String->String", outline(ast, 1));
    }

    @Test
    void add_reference_projection_narrows_return_type_to_number() {
        AST ast = parse("""
                let add = <a>(x:a,y:a)->x+y;
                let zN = add<Number>;
                """);
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
        assertEquals("Number->Number->Number", outline(ast, 1));
    }

    @Test
    void add_reference_projection_works_for_string_and_number_independently() {
        AST ast = parse("""
                let add = <a>(x:a,y:a)->x+y;
                let zS = add<String>;
                let zN = add<Number>;
                """);
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
        assertEquals("String->String->String", outline(ast, 1));
        assertEquals("Number->Number->Number", outline(ast, 2));
    }

    /**
     * KNOWN ISSUE — pinning the current (buggy) behavior so regressions don't
     * go silent.
     *
     * <p>For {@code add} the body's {@code +} accumulates
     * {@code addDefinedToBe(String|Number)} on the generic {@code a}. When
     * instantiating with {@code Integer} (a subtype of {@code Number}), the
     * reference check at {@code FirstOrderFunction.project} line 150
     * ({@code you.outline().is(me)}) currently rejects it even though
     * {@code Integer is Number is String|Number} should hold structurally.
     *
     * <p>Parallel operator {@code -} constrains {@code a} to {@code Number}
     * only (no union), and {@code Integer.is(Number)} passes — confirming the
     * bug is specific to union constraints on the reference.
     *
     * <p>Once the Reference/union subtyping is fixed, change this test to
     * assert {@code Integer->Integer->Integer} and drop {@code @Disabled}.
     */
    @Test
    void add_integer_projection_currently_rejected_known_issue() {
        AST ast = parse("""
                let add = <a>(x:a,y:a)->x+y;
                let zI = add<Integer>;
                """);
        assertFalse(ast.errors().isEmpty(),
                "if this now passes, the Reference/union subtyping bug has been fixed "
                        + "— flip this test to assert Integer->Integer->Integer");
        assertTrue(ast.errors().stream().anyMatch(
                        e -> e.toString().contains("reference type mismatch")),
                "expected the current reference-mismatch rejection, got: " + ast.errors());
    }

    @Test
    @Disabled("TODO: fix Reference.is() to recognise subtypes of union constraints " +
            "(Integer <: Number <: String|Number). Tracked in companion issue.")
    void add_integer_projection_should_narrow_to_integer() {
        AST ast = parse("""
                let add = <a>(x:a,y:a)->x+y;
                let zI = add<Integer>;
                """);
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
        assertEquals("Integer->Integer->Integer", outline(ast, 1));
    }
}
