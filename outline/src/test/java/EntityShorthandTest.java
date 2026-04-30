import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.gcp.outline.adt.Entity;
import org.twelve.gcp.outline.adt.EntityMember;
import org.twelve.gcp.outline.primitive.INTEGER;
import org.twelve.gcp.outline.primitive.STRING;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.*;
import static org.twelve.gcp.common.Tool.cast;

/**
 * Covers entity-literal field shorthand: {@code {a, b}} desugars to
 * {@code {a: a, b: b}}; mixed forms like {@code {a, b: 2}} are also allowed.
 *
 * <p>Single-field shorthand ({@code {a}} or {@code {a,}}) is intentionally
 * NOT supported because it is ambiguous with a block expression returning
 * {@code a}; such cases either parse as a block (when valid) or surface as a
 * parse error.
 */
public class EntityShorthandTest {

    @BeforeAll
    static void warmUp() {
        ASTHelper.parser.toString();
    }

    private static AST parse(String code) {
        return new OutlineParser().parse(new ASF(), code);
    }

    private static Entity firstEntityFromVar(AST ast, int idx) {
        VariableDeclarator var = cast(ast.program().body().statements().get(idx));
        return cast(var.assignments().getFirst().lhs().outline());
    }

    @Test
    void multi_field_shorthand_desugars_to_self_reference() {
        AST ast = parse("""
                let name = "will";
                let age = 10;
                let p = {name, age};
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors but got: " + ast.errors());

        Entity p = firstEntityFromVar(ast, 2);
        EntityMember nameM = p.getMember("name").orElseThrow();
        EntityMember ageM = p.getMember("age").orElseThrow();
        assertInstanceOf(STRING.class, nameM.outline());
        assertInstanceOf(INTEGER.class, ageM.outline());
    }

    @Test
    void mixed_shorthand_and_explicit_assignment_is_allowed() {
        AST ast = parse("""
                let name = "will";
                let p = {name, age: 10};
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors but got: " + ast.errors());

        Entity p = firstEntityFromVar(ast, 1);
        assertInstanceOf(STRING.class, p.getMember("name").orElseThrow().outline());
        assertInstanceOf(INTEGER.class, p.getMember("age").orElseThrow().outline());
    }

    @Test
    void mixed_explicit_first_then_shorthand_is_allowed() {
        AST ast = parse("""
                let age = 10;
                let p = {name: "will", age};
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors but got: " + ast.errors());

        Entity p = firstEntityFromVar(ast, 1);
        assertInstanceOf(STRING.class, p.getMember("name").orElseThrow().outline());
        assertInstanceOf(INTEGER.class, p.getMember("age").orElseThrow().outline());
    }

    @Test
    void single_field_shorthand_with_trailing_comma_is_rejected() {
        // {a,} is not a valid block and shorthand requires >= 2 members.
        assertThrows(RuntimeException.class, () -> parse("""
                let a = 1;
                let p = {a,};
                """));
    }

    @Test
    void single_braced_identifier_parses_as_block_not_entity() {
        // {a} stays as a block expression returning a (existing behaviour).
        AST ast = parse("""
                let a = 1;
                let p = {a};
                """);
        assertTrue(ast.asf().infer());
        // p should be Integer (block result), not an entity.
        VariableDeclarator var = cast(ast.program().body().statements().get(1));
        assertInstanceOf(INTEGER.class, var.assignments().getFirst().lhs().outline());
    }
}
