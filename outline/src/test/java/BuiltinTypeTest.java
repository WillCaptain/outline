import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.node.expression.Assignment;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.gcp.outline.Outline;
import org.twelve.gcp.outline.primitive.*;
import org.twelve.outline.OutlineParser;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.twelve.gcp.common.Tool.cast;

/**
 * Verifies that each built-in method resolves to the correct return type.
 */
public class BuiltinTypeTest {

    @BeforeAll
    static void warmUp() {
        ASTHelper.parser.toString();
    }

    private static Outline lhsOf(AST ast, int stmtIndex) {
        VariableDeclarator decl = cast(ast.program().body().statements().get(stmtIndex));
        return decl.assignments().getFirst().lhs().outline();
    }

    // ─────────────────────── Number built-ins ───────────────────────────────

    @Test
    void numberBuiltins() throws IOException {
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), """
            let x = 100;
            let a = x.abs();
            let b = x.ceil();
            let c = x.pow(2.0);
            """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "errors: " + ast.errors());

        assertInstanceOf(NUMBER.class,   lhsOf(ast, 1));  // abs  -> Number
        assertInstanceOf(INTEGER.class,  lhsOf(ast, 2));  // ceil -> Integer
        assertInstanceOf(DOUBLE.class,   lhsOf(ast, 3));  // pow  -> Double
    }

    // ─────────────────────── String built-ins ───────────────────────────────

    @Test
    void stringBuiltins() throws IOException {
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), """
            let s = "hello";
            let a = s.len();
            let b = s.split(",");
            let c = s.contains("x");
            let d = s.sub_str(1,3);
            """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "errors: " + ast.errors());

        assertInstanceOf(INTEGER.class,  lhsOf(ast, 1));          // len      -> Integer
        assertEquals("[String]",         lhsOf(ast, 2).toString()); // split   -> [String]
        assertInstanceOf(BOOL.class,     lhsOf(ast, 3));           // contains -> Bool
        assertInstanceOf(STRING.class,   lhsOf(ast, 4));           // sub_str  -> String
    }

    // ─────────────────────── Array built-ins: some/every/take_while/drop_while ─

    @Test
    void arrayFunctionalBuiltins() throws IOException {
        // some: (T → Bool) → Bool
        AST ast1 = RunnerHelper.parse("let n=[1,3,5]; let a=n.some(x->x>2);");
        assertTrue(ast1.asf().infer()); assertTrue(ast1.errors().isEmpty(), "some: " + ast1.errors());
        assertInstanceOf(BOOL.class, lhsOf(ast1, 1));

        // every: (T → Bool) → Bool
        AST ast2 = RunnerHelper.parse("let n=[1,3,5]; let b=n.every(x->x>0);");
        assertTrue(ast2.asf().infer()); assertTrue(ast2.errors().isEmpty(), "every: " + ast2.errors());
        assertInstanceOf(BOOL.class, lhsOf(ast2, 1));

        // take_while: (T → Bool) → [T]   — use > to avoid '<' grammar ambiguity with generics
        AST ast3 = RunnerHelper.parse("let n=[11,8,5,3,1]; let c=n.take_while(x->x>6);");
        assertTrue(ast3.asf().infer()); assertTrue(ast3.errors().isEmpty(), "take_while: " + ast3.errors());
        assertEquals("[Integer]", lhsOf(ast3, 1).toString());

        // drop_while: (T → Bool) → [T]
        AST ast4 = RunnerHelper.parse("let n=[11,8,5,3,1]; let d=n.drop_while(x->x>6);");
        assertTrue(ast4.asf().infer()); assertTrue(ast4.errors().isEmpty(), "drop_while: " + ast4.errors());
        assertEquals("[Integer]", lhsOf(ast4, 1).toString());
    }

    // ─────────────────────── Dict built-ins ─────────────────────────────────

    @Test
    void dictBuiltins() throws IOException {
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), """
            let d = ["a":1,"b":2];
            let a = d.len();
            let b = d.keys();
            """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "errors: " + ast.errors());

        assertInstanceOf(INTEGER.class,  lhsOf(ast, 1));           // len  -> Integer
        assertEquals("[String]",         lhsOf(ast, 2).toString()); // keys -> [String]
    }
}
