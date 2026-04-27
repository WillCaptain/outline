import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.gcp.outline.Outline;
import org.twelve.gcp.outline.adt.Entity;
import org.twelve.gcp.outline.adt.EntityMember;
import org.twelve.gcp.outline.primitive.INTEGER;
import org.twelve.gcp.outline.primitive.STRING;
import org.twelve.outline.OutlineParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.twelve.gcp.common.Tool.cast;

/**
 * Covers the JS-style entity literal syntax {@code {name: "will", age: 10}}
 * added alongside the existing outline-style {@code {name = "will", age = 10}}.
 *
 * <p>Note: outline's type-declaration syntax {@code {name: String, age: Int}}
 * uses the same colon separator but appears only in type positions (e.g. inside
 * {@code outline X = {...}} or a type annotation), so it is unaffected.
 *
 * <p>Trailing commas (e.g. {@code {name:"will",}}) are rejected in all three
 * forms and should surface as a parse error on the AST.
 */
public class EntityJsSyntaxTest {

    @BeforeAll
    static void warmUp() {
        ASTHelper.parser.toString();
    }

    private static AST parse(String code) {
        return new OutlineParser().parse(new ASF(), code);
    }

    private static Entity firstEntity(AST ast) {
        VariableDeclarator var = cast(ast.program().body().statements().getFirst());
        return cast(var.assignments().getFirst().lhs().outline());
    }

    @Test
    void js_style_entity_with_colon_separator_infers_member_types() {
        AST ast = parse("""
                let p = {name: "will", age: 10};
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors but got: " + ast.errors());

        Entity p = firstEntity(ast);
        List<EntityMember> ms = p.members().stream().filter(m -> !m.isDefault()).toList();
        assertEquals(2, ms.size(), "entity should have exactly two members");

        EntityMember name = p.getMember("name").orElseThrow();
        EntityMember age = p.getMember("age").orElseThrow();
        assertInstanceOf(STRING.class, name.outline(),
                "name should be inferred as String");
        assertInstanceOf(INTEGER.class, age.outline(),
                "age should be inferred as Integer");
    }

    @Test
    void outline_style_entity_with_equal_separator_still_works() {
        AST ast = parse("""
                let p = {name = "will", age = 10};
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors but got: " + ast.errors());

        Entity p = firstEntity(ast);
        assertInstanceOf(STRING.class, p.getMember("name").orElseThrow().outline());
        assertInstanceOf(INTEGER.class, p.getMember("age").orElseThrow().outline());
    }

    @Test
    void js_and_outline_separators_can_be_mixed_within_one_entity() {
        // A single entity is free to mix `:` and `=` – both are recognised
        // by property_assignment.
        AST ast = parse("""
                let p = {name: "will", age = 10};
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors but got: " + ast.errors());

        Entity p = firstEntity(ast);
        assertInstanceOf(STRING.class, p.getMember("name").orElseThrow().outline());
        assertInstanceOf(INTEGER.class, p.getMember("age").orElseThrow().outline());
    }

    @Test
    void js_style_entity_colon_accepts_qualified_enum_value() {
        AST ast = parse("""
                outline EmployeeStatus = INITIALIZED|ACTIVE|INACTIVE;
                let p = {status: EmployeeStatus.INITIALIZED, sync_status = "internal"};
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors but got: " + ast.errors());
    }

    @Test
    void entity_copy_with_enum_override_can_project_to_struct_parameter() {
        AST ast = parse("""
                outline EmployeeStatus = INITIALIZED|ACTIVE|INACTIVE;
                let create = (x:{name:String, email:String, status:EmployeeStatus, sync_status:String})->x;
                let employee = {
                  name = "Will",
                  email = "will@example.com",
                  status = EmployeeStatus.INITIALIZED,
                  sync_status = "external"
                };
                create(employee{status:EmployeeStatus.INITIALIZED, sync_status = "internal"});
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors but got: " + ast.errors());
    }

    @Test
    void trailing_comma_in_js_style_entity_is_rejected() {
        // The parser raises a syntax error as soon as it sees the dangling ','
        // before the closing '}' – trailing commas are disallowed by grammar.
        RuntimeException ex = assertThrows(RuntimeException.class, () -> parse("""
                let p = {name: "will", age: 10,};
                """));
        assertTrue(ex.getMessage() == null
                        || ex.getMessage().contains("}")
                        || ex.getMessage().contains("grammar"),
                "Unexpected parse error for trailing comma: " + ex.getMessage());
    }

    @Test
    void trailing_comma_in_outline_style_entity_is_rejected() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> parse("""
                let p = {name = "will", age = 10,};
                """));
        assertTrue(ex.getMessage() == null
                        || ex.getMessage().contains("}")
                        || ex.getMessage().contains("grammar"),
                "Unexpected parse error for trailing comma: " + ex.getMessage());
    }
}
