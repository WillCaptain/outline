import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.node.expression.Assignment;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.gcp.outline.Outline;
import org.twelve.gcp.outline.adt.SumADT;
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

    // ─────────────────────── Nullable type (T?) ─────────────────────────────

    @Test
    void nullable_type_declared_as_option_type() throws IOException {
        // "String?" in a declared type should parse to String|Nothing
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), """
            outline Tag = {
                id:0,
                name:String,
                note:String?
            };
            """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "errors: " + ast.errors());

        // "note" field should have type String|Nothing (a SumADT containing Nothing)
        var tag = ast.symbolEnv().lookupAll("Tag");
        assertNotNull(tag, "Tag outline should be defined");
        var tagEntity = (org.twelve.gcp.outline.adt.Entity) tag.outline();
        var noteMember = tagEntity.getMember("note");
        assertTrue(noteMember.isPresent(), "note field should exist");
        assertInstanceOf(SumADT.class, noteMember.get().outline(),
                "note field type should be a union (String|Nothing)");
        SumADT noteType = (SumADT) noteMember.get().outline();
        assertTrue(noteType.options().stream().anyMatch(o -> o instanceof NOTHING),
                "note field type should contain Nothing");
    }

    @Test
    void nullable_type_entity_field_creation_allows_missing() throws IOException {
        // Creating an entity while omitting nullable fields should not produce errors
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), """
            outline Tag = {
                id:0,
                name:String,
                note:String?
            };
            let t: {name:String} = {name = "hello"};
            """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(),
                "omitting nullable field in struct literal should be error-free; errors: " + ast.errors());
    }

    @Test
    void nullable_type_in_inline_struct_func_param() throws IOException {
        // "Type?" should be valid in entity_field inside an inline struct used as a function parameter type.
        // E.g. update:{name: String?, price: Int?} -> Unit
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), """
            outline Employee = {
                id:0,
                name:String,
                price:Int,
                update:{name: String?, price: Int?} -> Unit
            };
            """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "inline struct with nullable fields should parse; errors: " + ast.errors());
    }
}
