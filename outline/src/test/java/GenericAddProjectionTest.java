import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
     * Built-in primitive name resolution: a bare identifier like
     * {@code Integer} in {@code <Integer>} must resolve to the canonical
     * INTEGER primitive (not a name-matched SYMBOL placeholder), so that
     * {@code Integer is Number is String|Number} subtype chains hold and
     * the reference projection succeeds.
     *
     * <p>Fix lives in
     * {@link org.twelve.gcp.inference.SymbolIdentifierInference#resolveBuiltin}.
     */
    @Test
    void add_integer_projection_narrows_to_integer() {
        AST ast = parse("""
                let add = <a>(x:a,y:a)->x+y;
                let zI = add<Integer>;
                """);
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
        assertEquals("Integer->Integer->Integer", outline(ast, 1));
    }

    @Test
    void add_long_projection_narrows_to_long() {
        AST ast = parse("""
                let add = <a>(x:a,y:a)->x+y;
                let zL = add<Long>;
                """);
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());
        assertEquals("Long->Long->Long", outline(ast, 1));
    }
}
