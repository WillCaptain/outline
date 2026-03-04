import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.OutlineParser;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AST#meta()} and {@link ASF#meta()} — JavaDoc-like schema extraction
 * including comments. The meta output is suitable for JSON export and LLM search indexing.
 */
public class MetaTest {

    private final OutlineParser parser = new OutlineParser();

    @Test
    void test_meta_module_name_and_namespace() {
        AST ast = parser.parse("""
            module org.example.user
            let x = 1;
            let y = 2;
            export x, y;
            """);
        Map<String, Object> meta = ast.meta();
        assertEquals("user", meta.get("name"));
        assertEquals("org.example", meta.get("namespace"));
    }

    @Test
    void test_meta_module_description_from_comment() {
        AST ast = parser.parse("""
            /**
             * User profile module.
             * Holds basic user data.
             */
            module org.example.profile
            let name = "Alice";
            let id = 1;
            export name, id;
            """);
        Map<String, Object> meta = ast.meta();
        assertEquals("profile", meta.get("name"));
        assertEquals("User profile module. Holds basic user data.", meta.get("description"));
    }

    @Test
    void test_meta_single_line_comment_description() {
        AST ast = parser.parse("""
            module m.helper
            // A constant used across the app
            let maxCount = 100;
            let min = 0;
            export maxCount, min;
            """);
        Map<String, Object> meta = ast.meta();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vars = (List<Map<String, Object>>) meta.get("variables");
        assertTrue(vars.size() >= 1);
        Map<String, Object> maxCount = vars.stream().filter(v -> "maxCount".equals(v.get("name"))).findFirst().orElse(null);
        assertNotNull(maxCount);
        assertEquals("A constant used across the app", maxCount.get("description"));
    }

    @Test
    void test_meta_block_comment_on_variable() {
        AST ast = parser.parse("""
            module m.lib
            /*
             * The main entry point.
             * Call this to run the app.
             */
            let main = () -> 42;
            let nop = () -> 0;
            export main, nop;
            """);
        Map<String, Object> meta = ast.meta();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> funcs = (List<Map<String, Object>>) meta.get("functions");
        assertTrue(funcs.size() >= 1);
        Map<String, Object> main = funcs.stream().filter(f -> "main".equals(f.get("name"))).findFirst().orElse(null);
        assertNotNull(main);
        assertEquals("The main entry point. Call this to run the app.", main.get("description"));
    }

    @Test
    void test_meta_imports_and_exports() {
        AST ast = parser.parse("""
            module org.app.consumer
            import grade as level, school from org.app.education;
            let x = level;
            let y = school;
            export x as result, y;
            """);
        Map<String, Object> meta = ast.meta();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> imports = (List<Map<String, Object>>) meta.get("imports");
        assertEquals(2, imports.size());
        assertEquals("grade", imports.get(0).get("symbol"));
        assertEquals("level", imports.get(0).get("as"));
        assertEquals("education", imports.get(0).get("from"));
        assertEquals("school", imports.get(1).get("symbol"));
        assertNull(imports.get(1).get("as"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exports = (List<Map<String, Object>>) meta.get("exports");
        assertTrue(exports.size() >= 1);
        Map<String, Object> xExport = exports.stream().filter(e -> "x".equals(e.get("name"))).findFirst().orElse(null);
        assertNotNull(xExport);
        assertEquals("result", xExport.get("as"));
    }

    @Test
    void test_meta_variables_with_type_and_mutable() {
        AST ast = parser.parse("""
            module m.demo
            let a: Integer = 1;
            var b = 2;
            let s = "hi";
            export a, b;
            """);
        ast.asf().infer();
        Map<String, Object> meta = ast.meta();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vars = (List<Map<String, Object>>) meta.get("variables");
        assertTrue(vars.size() >= 3);
        // Order may vary; find by name
        Map<String, Object> a = vars.stream().filter(v -> "a".equals(v.get("name"))).findFirst().orElse(null);
        Map<String, Object> b = vars.stream().filter(v -> "b".equals(v.get("name"))).findFirst().orElse(null);
        assertNotNull(a);
        assertNotNull(b);
        assertEquals("let", a.get("kind"));
        assertEquals("var", b.get("kind"));
        assertFalse((Boolean) a.get("mutable"));
        assertTrue((Boolean) b.get("mutable"));
    }

    @Test
    void test_asf_meta_multiple_modules() {
        ASF asf = new ASF();
        parser.parse(asf, """
            module p.a
            let v = 1;
            let u = 2;
            export v, u;
            """);
        parser.parse(asf, """
            module p.b
            import v from a;
            let w = v;
            let x = 1;
            export w, x;
            """);
        Map<String, Object> meta = asf.meta();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) meta.get("modules");
        assertEquals(2, modules.size());
        assertEquals("a", modules.get(0).get("name"));
        assertEquals("b", modules.get(1).get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bImports = (List<Map<String, Object>>) modules.get(1).get("imports");
        assertEquals(1, bImports.size());
        assertEquals("v", bImports.get(0).get("symbol"));
        assertEquals("a", bImports.get(0).get("from"));
    }

    /**
     * Ontology example from entitir (CodeHelper.countryOutline style).
     * Ensures meta extraction works for entity/collection outline patterns
     * used in ontology applications.
     */
    @Test
    void test_meta_ontology_example() {
        // Same structure as entitir ontology: Country entity + Countries VirtualSet
        String code = """
            /**
             * Geo ontology: Country with provinces.
             * From entitir CodeHelper / OntologyWorldSeed.
             */
            module org.example.geo
            outline Country = { id:0, name:String, code:String, provinces:Unit -> Provinces };
            outline Countries = VirtualSet<Country>{ provinces:Unit -> Provinces };
            // Repo-backed collection
            let countries = __ontology_repo__<Countries>;
            // Memo-backed entity constructor
            let country = __ontology_memo__<Country>;
            export countries, country;
            """;
        AST ast = parser.parse(code);
        Map<String, Object> meta = ast.meta();

        assertEquals("geo", meta.get("name"));
        assertEquals("org.example", meta.get("namespace"));
        assertEquals("Geo ontology: Country with provinces. From entitir CodeHelper / OntologyWorldSeed.", meta.get("description"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vars = (List<Map<String, Object>>) meta.get("variables");
        assertTrue(vars.size() >= 2);
        assertTrue(vars.stream().anyMatch(v -> "countries".equals(v.get("name"))));
        assertTrue(vars.stream().anyMatch(v -> "country".equals(v.get("name"))));
    }

    @Test
    void test_meta_ast_without_source_still_works() {
        ASF asf = new ASF();
        AST ast = asf.newAST();
        org.twelve.gcp.node.statement.VariableDeclarator vd =
                new org.twelve.gcp.node.statement.VariableDeclarator(ast, org.twelve.gcp.common.VariableKind.LET);
        vd.declare(new org.twelve.gcp.node.expression.identifier.Identifier(ast, new org.twelve.gcp.ast.Token<>("x")),
                org.twelve.gcp.node.expression.LiteralNode.parse(ast, new org.twelve.gcp.ast.Token<>(1)));
        ast.addStatement(vd);
        // No setSourceCode - built programmatically
        Map<String, Object> meta = ast.meta();
        assertNull(ast.sourceCode());
        assertNull(meta.get("description"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vars = (List<Map<String, Object>>) meta.get("variables");
        assertFalse(vars.isEmpty());
    }
}
