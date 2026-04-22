import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@code let}/{@code var} prefixes in entity-type field
 * declarations such as {@code outline Animal = { var age: Int, name: String };}.
 *
 * <p>Previously the grammar's {@code entity_field} rule accepted only {@code ID (':' ...)?},
 * so adding {@code let}/{@code var} raised a syntax error. The rule now permits an
 * optional {@code ('let'|'var')} mutability keyword to mirror how entity literals
 * already support it ({@code {let x=1, var y=2}}).
 */
class TypeDeclFieldMutabilityTest {

    private static AST parse(String code) {
        AST ast = new OutlineParser().parse(code);
        ast.asf().infer();
        return ast;
    }

    @Test
    void var_prefix_parses_in_type_declaration() {
        AST ast = parse("""
                outline Animal = { var age: Int, name: String };
                let a = Animal{ age = 10, name = "w" };
                """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
        assertEquals(2, ast.program().body().nodes().size());
    }

    @Test
    void let_prefix_parses_in_type_declaration() {
        AST ast = parse("""
                outline Config = { let host: String, port: Int };
                let c = Config{ host = "localhost", port = 8080 };
                """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }

    @Test
    void mixed_let_var_prefixes_allowed() {
        AST ast = parse("""
                outline Counter = {
                    let name: String,
                    var value: Int,
                    step: Int
                };
                let c = Counter{ name = "c", value = 0, step = 1 };
                """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }

    @Test
    void no_mutability_keyword_still_works() {
        AST ast = parse("""
                outline Point = { x: Int, y: Int };
                let p = Point{ x = 1, y = 2 };
                """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }

    @Test
    void var_with_literal_default_also_parses() {
        AST ast = parse("""
                outline Flags = { var enabled: #true, name: String };
                """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }
}
