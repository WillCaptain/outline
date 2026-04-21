import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.gcp.outline.projectable.FirstOrderFunction;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.twelve.gcp.common.Tool.cast;

/**
 * Regression for the function-typed-parameter return inference bug.
 *
 * <p>Before the fix, a lambda whose single parameter carried an explicit
 * concrete function-type annotation would infer its return type as
 * {@code null} at <em>definition</em> time, because
 * {@link org.twelve.gcp.inference.FunctionCallInference}'s
 * {@code project(Genericable, argument)} consulted the virtual
 * {@code HigherOrderFunction} placeholder in {@code definedToBe} (whose
 * return is an unresolved {@code Return} slot) before the user's actual
 * annotation in {@code declaredToBe}. The call site — e.g.
 * {@code z(b->100)} — would later recover the correct type via FOF
 * projection, but {@code z.outline()} at its declaration stall-rendered
 * as {@code (String->Integer)->null}.
 *
 * <p>Fix: when the generic's {@code declaredToBe} is a
 * {@link FirstOrderFunction} whose formal argument's {@code min()} is
 * fully concrete (no unresolved type variable from an enclosing generic),
 * short-circuit to the FOF projection path so the return type is resolved
 * directly from the annotation.
 */
public class FuncTypedParamReturnTest {

    @Test
    void lambda_with_func_typed_param_resolves_return_at_definition_time() {
        OutlineParser parser = new OutlineParser();
        String code = """
                let z1 = (x:(String->Integer))->{
                  x("will")
                };
                let z2 = (x:(String->Integer))-> x("will");
                let a1 = z1(b->100);
                let a2 = z2(b->100);
                """;
        AST ast = parser.parse(new ASF(), code);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "unexpected errors: " + ast.errors());

        for (int idx : new int[]{0, 1}) {
            VariableDeclarator decl = cast(ast.program().body().statements().get(idx));
            FirstOrderFunction fof = cast(decl.assignments().getFirst().lhs().outline());
            assertEquals("(String->Integer)->Integer", fof.toString(),
                    "z" + (idx + 1) + " definition-time outline must resolve return to Integer, not null");
        }

        VariableDeclarator a1 = cast(ast.program().body().statements().get(2));
        VariableDeclarator a2 = cast(ast.program().body().statements().get(3));
        assertEquals("Integer", a1.assignments().getFirst().lhs().outline().toString());
        assertEquals("Integer", a2.assignments().getFirst().lhs().outline().toString());
    }
}
