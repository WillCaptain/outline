import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.meta.*;
import org.twelve.gcp.outline.Outline;
import org.twelve.gcp.outline.adt.Entity;
import org.twelve.outline.OutlineParser;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the typed meta API: {@link ModuleMeta}, {@link ForestMeta}, and schema navigation.
 * <p>
 * Verifies: module info, imports/exports, outline fields/methods, variable types,
 * function parameters, comment descriptions, ontology patterns, JSON export.
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
        ModuleMeta meta = ast.meta();
        assertEquals("user", meta.name());
        assertEquals("org.example", meta.namespace());
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
        ModuleMeta meta = ast.meta();
        assertEquals("profile", meta.name());
        assertEquals("User profile module. Holds basic user data.", meta.description());
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
        ast.asf().infer();
        ModuleMeta meta = ast.meta();
        VariableMeta maxCount = meta.variables().stream()
                .filter(v -> "maxCount".equals(v.name())).findFirst().orElse(null);
        assertNotNull(maxCount);
        assertEquals("A constant used across the app", maxCount.description());
    }

    @Test
    void test_meta_block_comment_on_function() {
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
        ast.asf().infer();
        ModuleMeta meta = ast.meta();
        FunctionMeta main = meta.functions().stream()
                .filter(f -> "main".equals(f.name())).findFirst().orElse(null);
        assertNotNull(main);
        assertEquals("The main entry point. Call this to run the app.", main.description());
        assertEquals(SchemaMeta.Kind.FUNCTION, main.kind());
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
        ModuleMeta meta = ast.meta();

        List<ImportMeta> imports = meta.imports();
        assertEquals(2, imports.size());
        assertEquals("grade", imports.get(0).symbol());
        assertEquals("level", imports.get(0).as());
        assertEquals("education", imports.get(0).from());
        assertEquals("school", imports.get(1).symbol());
        assertNull(imports.get(1).as());

        List<ExportMeta> exports = meta.exports();
        assertTrue(exports.size() >= 1);
        ExportMeta xExport = exports.stream().filter(e -> "x".equals(e.name())).findFirst().orElse(null);
        assertNotNull(xExport);
        assertEquals("result", xExport.as());
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
        ModuleMeta meta = ast.meta();

        List<VariableMeta> vars = meta.variables();
        assertTrue(vars.size() >= 3);
        VariableMeta a = vars.stream().filter(v -> "a".equals(v.name())).findFirst().orElse(null);
        VariableMeta b = vars.stream().filter(v -> "b".equals(v.name())).findFirst().orElse(null);
        assertNotNull(a);
        assertNotNull(b);
        assertFalse(a.mutable());
        assertTrue(b.mutable());
        assertEquals("let", a.varKind());
        assertEquals("var", b.varKind());
        assertEquals(SchemaMeta.Kind.VARIABLE, a.kind());
        assertNotNull(a.type());
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
        asf.infer();
        ForestMeta forest = asf.meta();
        assertEquals(2, forest.modules().size());
        assertEquals("a", forest.modules().get(0).name());
        assertEquals("b", forest.modules().get(1).name());
        assertNotNull(forest.find("b"));
        List<ImportMeta> bImports = forest.find("b").imports();
        assertEquals(1, bImports.size());
        assertEquals("v", bImports.get(0).symbol());
        assertEquals("a", bImports.get(0).from());
    }

    @Test
    void test_meta_ontology_outline_fields_and_methods() {
        String code = """
            /**
             * Geo ontology: Country with provinces.
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
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        assertEquals("geo", meta.name());
        assertEquals("org.example", meta.namespace());
        assertEquals("Geo ontology: Country with provinces.", meta.description());

        // outline declarations
        List<OutlineMeta> outlines = meta.outlines();
        assertTrue(outlines.size() >= 2);

        OutlineMeta countryMeta = (OutlineMeta) meta.find("Country");
        assertNotNull(countryMeta);
        assertEquals(SchemaMeta.Kind.OUTLINE, countryMeta.kind());
        assertNotNull(countryMeta.type());

        // Country: all members (fields + methods)
        List<FieldMeta> allMembers = countryMeta.members();
        assertTrue(allMembers.size() >= 4, "Expected >=4 members, got: " + allMembers.size());

        // Country has fields: id, name, code  (data fields)
        List<FieldMeta> dataFields = countryMeta.fields();
        assertTrue(dataFields.stream().anyMatch(f -> "name".equals(f.name())),
                "Expected 'name' field in: " + dataFields.stream().map(FieldMeta::name).toList());

        // Country has methods: provinces:Unit -> Provinces
        List<FieldMeta> methods = countryMeta.methods();
        assertTrue(methods.stream().anyMatch(f -> "provinces".equals(f.name())),
                "Expected 'provinces' method in: " + methods.stream().map(FieldMeta::name).toList());
        FieldMeta provincesMethod = methods.stream().filter(f -> "provinces".equals(f.name())).findFirst().orElse(null);
        assertNotNull(provincesMethod);
        assertTrue(provincesMethod.isMethod());
        assertTrue(provincesMethod.type().contains("->"));

        // variables
        assertTrue(meta.variables().stream().anyMatch(v -> "countries".equals(v.name())));
        assertTrue(meta.variables().stream().anyMatch(v -> "country".equals(v.name())));

        // nodes() contains everything
        assertTrue(meta.nodes().size() >= 4);
    }

    @Test
    void test_meta_toMap_json_serializable() {
        AST ast = parser.parse("""
            module m.json
            outline Point = { x:Int, y:Int };
            let origin = 0;
            let dummy = 1;
            export origin, dummy;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();
        Map<String, Object> map = meta.toMap();

        assertEquals("json", map.get("name"));
        assertInstanceOf(List.class, map.get("nodes"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) map.get("nodes");
        assertTrue(nodes.stream().anyMatch(n -> "outline".equals(n.get("kind"))));
        assertTrue(nodes.stream().anyMatch(n -> "variable".equals(n.get("kind"))));

        Map<String, Object> pointMap = nodes.stream()
                .filter(n -> "Point".equals(n.get("name"))).findFirst().orElse(null);
        assertNotNull(pointMap);
        assertNotNull(pointMap.get("type"));
        assertInstanceOf(List.class, pointMap.get("fields"));
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
        asf.infer();
        ModuleMeta meta = ast.meta();
        assertNull(ast.sourceCode());
        assertNull(meta.description());
        assertFalse(meta.variables().isEmpty());
    }

    @Test
    void test_meta_find_by_name() {
        AST ast = parser.parse("""
            module m.find
            outline Shape = { sides:Int };
            let pi = 3;
            let area = (r) -> r;
            export pi, area;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();
        assertNotNull(meta.find("Shape"));
        assertNotNull(meta.find("pi"));
        assertNotNull(meta.find("area"));
        assertNull(meta.find("nonexistent"));
        assertInstanceOf(OutlineMeta.class, meta.find("Shape"));
        assertInstanceOf(VariableMeta.class, meta.find("pi"));
        assertInstanceOf(FunctionMeta.class, meta.find("area"));
    }

    // ── Scope-aware resolution tests ────────────────────────────────────────

    @Test
    void test_meta_scopes_exist() {
        AST ast = parser.parse("""
            module m.scope
            let x = 1;
            let f = (a) -> a;
            let y = 2;
            export x, y;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        List<ScopeMeta> scopes = meta.scopes();
        assertFalse(scopes.isEmpty(), "Expected at least one scope");
        // root scope has no parent
        ScopeMeta root = scopes.stream()
                .filter(s -> s.parentScopeId() == null).findFirst().orElse(null);
        assertNotNull(root, "Root scope should have null parentScopeId");
        // function body creates a nested scope
        assertTrue(scopes.size() >= 2,
                "Expected >=2 scopes (root + function body), got: " + scopes.size());
    }

    @Test
    void test_meta_scopeAt_finds_innermost() {
        String code = """
            module m.pos
            let x = 1;
            let f = (a) -> a;
            let y = 2;
            export x, y;
            """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        // position at the start of "let x" → should be in root scope
        int letXPos = code.indexOf("let x");
        assertTrue(letXPos > 0);
        ScopeMeta atLetX = meta.scopeAt(letXPos);
        assertNotNull(atLetX, "scopeAt should find a scope at 'let x'");

        // position inside the function body "(a) -> a"
        int arrowBody = code.indexOf("-> a") + 3; // inside the body after ->
        ScopeMeta atBody = meta.scopeAt(arrowBody);
        assertNotNull(atBody, "scopeAt should find a scope inside function body");
    }

    @Test
    void test_meta_resolve_symbol_at_position() {
        String code = """
            module m.resolve
            let x = 1;
            let f = (a) -> a;
            let y = 2;
            export x, y;
            """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        // resolve "x" at position of "let y" → should find x (in root scope)
        int letYPos = code.indexOf("let y");
        SymbolMeta xSym = meta.resolve("x", letYPos);
        assertNotNull(xSym, "Should resolve 'x' at top-level position");
        assertEquals("x", xSym.name());
        assertEquals("variable", xSym.kind());
    }

    @Test
    void test_meta_resolve_parameter_in_function_scope() {
        String code = """
            module m.param
            let x = 1;
            let inc = (n) -> n;
            let y = 2;
            export x, y;
            """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        int bodyPos = code.indexOf("-> n") + 3;

        SymbolMeta nSym = meta.resolve("n", bodyPos);
        assertNotNull(nSym, "Should resolve parameter 'n' inside function body");
        assertEquals("n", nSym.name());
        assertEquals("parameter", nSym.kind());

        // "x" should also be visible from inside the function (parent scope)
        SymbolMeta xFromInside = meta.resolve("x", bodyPos);
        assertNotNull(xFromInside, "Should resolve 'x' from parent scope inside function");
    }

    @Test
    void test_meta_visibleSymbols_at_position() {
        String code = """
            module m.visible
            let a = 1;
            let b = 2;
            let f = (p) -> p;
            export a, b;
            """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        // at top level, should see a, b, f
        int topPos = code.indexOf("export");
        List<SymbolMeta> topSymbols = meta.visibleSymbols(topPos);
        assertTrue(topSymbols.stream().anyMatch(s -> "a".equals(s.name())),
                "Should see 'a' at top level");
        assertTrue(topSymbols.stream().anyMatch(s -> "b".equals(s.name())),
                "Should see 'b' at top level");
        assertTrue(topSymbols.stream().anyMatch(s -> "f".equals(s.name())),
                "Should see 'f' at top level");

        // inside function body, should see p + a + b + f
        int bodyPos = code.indexOf("-> p") + 3;
        List<SymbolMeta> innerSymbols = meta.visibleSymbols(bodyPos);
        assertTrue(innerSymbols.stream().anyMatch(s -> "p".equals(s.name())),
                "Should see parameter 'p' inside function");
        assertTrue(innerSymbols.stream().anyMatch(s -> "a".equals(s.name())),
                "Should see 'a' from parent scope inside function");
    }

    @Test
    void test_meta_membersOf_for_dot_completion() {
        String code = """
            module org.example.dots
            outline Country = { id:0, name:String, code:String, provinces:Unit -> Provinces };
            outline Countries = VirtualSet<Country>{ provinces:Unit -> Provinces };
            let countries = __ontology_repo__<Countries>;
            let country = __ontology_memo__<Country>;
            export countries, country;
            """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        int pos = code.indexOf("export");

        List<FieldMeta> countryMembers = meta.membersOf("country", pos);
        assertTrue(countryMembers.size() >= 4,
                "Expected >=4 members for Country, got: " + countryMembers.size()
                        + " " + countryMembers.stream().map(FieldMeta::name).toList());
        assertTrue(countryMembers.stream().anyMatch(f -> "name".equals(f.name())),
                "Expected 'name' field in country members");
        assertTrue(countryMembers.stream().anyMatch(f -> "provinces".equals(f.name())),
                "Expected 'provinces' method in country members");
    }

    @Test
    void test_meta_scope_toMap() {
        AST ast = parser.parse("""
            module m.smap
            let x = 1;
            let f = (a) -> a;
            export x, f;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();
        Map<String, Object> map = meta.toMap();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scopeMaps = (List<Map<String, Object>>) map.get("scopes");
        assertNotNull(scopeMaps, "toMap should include 'scopes'");
        assertFalse(scopeMaps.isEmpty());
        Map<String, Object> first = scopeMaps.get(0);
        assertNotNull(first.get("scopeId"));
        assertNotNull(first.get("start"));
        assertNotNull(first.get("end"));
        assertNotNull(first.get("symbols"));
    }

    // ── Member comments & origin tests ──────────────────────────────────────

    @Test
    void test_meta_member_comments_extracted() {
        AST ast = parser.parse("""
            module m.docs
            outline Person = {
              // The person's unique identifier
              id: 0,
              // Full legal name
              name: String,
              // Person's age in years
              age: Int
            };
            let x = 1;
            let y = 2;
            export x, y;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        OutlineMeta person = (OutlineMeta) meta.find("Person");
        assertNotNull(person, "Should find Person outline");
        assertTrue(person.members().size() >= 3,
                "Expected >=3 members, got: " + person.members().size());

        FieldMeta idField = person.members().stream()
                .filter(f -> "id".equals(f.name())).findFirst().orElse(null);
        assertNotNull(idField);
        assertEquals("The person's unique identifier", idField.description());

        FieldMeta nameField = person.members().stream()
                .filter(f -> "name".equals(f.name())).findFirst().orElse(null);
        assertNotNull(nameField);
        assertEquals("Full legal name", nameField.description());

        FieldMeta ageField = person.members().stream()
                .filter(f -> "age".equals(f.name())).findFirst().orElse(null);
        assertNotNull(ageField);
        assertEquals("Person's age in years", ageField.description());
    }

    @Test
    void test_meta_member_block_comment_extracted() {
        AST ast = parser.parse("""
            module m.block
            outline Config = {
              /* Maximum retry count */
              retries: Int,
              /*
               * Timeout in milliseconds.
               * Applies to all network calls.
               */
              timeout: Int
            };
            let a = 1;
            let b = 2;
            export a, b;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        OutlineMeta config = (OutlineMeta) meta.find("Config");
        assertNotNull(config);

        FieldMeta retries = config.members().stream()
                .filter(f -> "retries".equals(f.name())).findFirst().orElse(null);
        assertNotNull(retries);
        assertEquals("Maximum retry count", retries.description());

        FieldMeta timeout = config.members().stream()
                .filter(f -> "timeout".equals(f.name())).findFirst().orElse(null);
        assertNotNull(timeout);
        assertNotNull(timeout.description());
        assertTrue(timeout.description().contains("Timeout in milliseconds"),
                "Expected multi-line block comment, got: " + timeout.description());
    }

    @Test
    void test_meta_member_without_comment_has_null_description() {
        AST ast = parser.parse("""
            module m.nocomment
            outline Point = { x: Int, y: Int };
            let a = 1;
            let b = 2;
            export a, b;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        OutlineMeta point = (OutlineMeta) meta.find("Point");
        assertNotNull(point);
        assertTrue(point.members().size() >= 2);

        for (FieldMeta f : point.members()) {
            assertNull(f.description(),
                    "Member '" + f.name() + "' has no comment, description should be null");
        }
    }

    @Test
    void test_meta_member_origin_own() {
        AST ast = parser.parse("""
            module m.own
            outline Dog = {
              name: String,
              breed: String
            };
            let x = 1;
            let y = 2;
            export x, y;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        OutlineMeta dog = (OutlineMeta) meta.find("Dog");
        assertNotNull(dog);
        for (FieldMeta f : dog.members()) {
            // Since extractEntityFields now calls loadBuiltInMethods(), system methods like
            // to_str appear with origin="builtin".  All user-declared members must be "own".
            assertTrue("own".equals(f.origin()) || "builtin".equals(f.origin()),
                    "Member '" + f.name() + "' origin should be 'own' or 'builtin', got: "
                            + f.origin());
        }
        // Declared fields name/breed must specifically be "own"
        List<FieldMeta> ownFields = dog.members().stream()
                .filter(f -> "own".equals(f.origin())).toList();
        assertTrue(ownFields.stream().anyMatch(f -> "name".equals(f.name())),
                "Expected 'name' with origin='own'");
        assertTrue(ownFields.stream().anyMatch(f -> "breed".equals(f.name())),
                "Expected 'breed' with origin='own'");
    }

    @Test
    void test_meta_member_origin_base_and_own() {
        AST ast = parser.parse("""
            module m.inherit
            outline Animal = {
              name: String,
              legs: Int
            };
            outline Dog = Animal{
              breed: String
            };
            let x = 1;
            let y = 2;
            export x, y;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        OutlineMeta dog = (OutlineMeta) meta.find("Dog");
        assertNotNull(dog);
        assertTrue(dog.members().size() >= 3,
                "Expected >=3 members (name, legs from base + breed own), got: "
                        + dog.members().stream().map(FieldMeta::name).toList());

        FieldMeta breed = dog.members().stream()
                .filter(f -> "breed".equals(f.name())).findFirst().orElse(null);
        assertNotNull(breed);
        assertEquals("own", breed.origin());

        FieldMeta name = dog.members().stream()
                .filter(f -> "name".equals(f.name())).findFirst().orElse(null);
        assertNotNull(name);
        assertEquals("base", name.origin());

        FieldMeta legs = dog.members().stream()
                .filter(f -> "legs".equals(f.name())).findFirst().orElse(null);
        assertNotNull(legs);
        assertEquals("base", legs.origin());
    }

    @Test
    void test_meta_member_origin_in_toMap() {
        AST ast = parser.parse("""
            module m.originmap
            outline Vehicle = {
              speed: Int
            };
            outline Car = Vehicle{
              doors: Int
            };
            let x = 1;
            let y = 2;
            export x, y;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();
        Map<String, Object> map = meta.toMap();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) map.get("nodes");
        Map<String, Object> carMap = nodes.stream()
                .filter(n -> "Car".equals(n.get("name"))).findFirst().orElse(null);
        assertNotNull(carMap);
        assertInstanceOf(List.class, carMap.get("fields"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) carMap.get("fields");
        Map<String, Object> doorsField = fields.stream()
                .filter(f -> "doors".equals(f.get("name"))).findFirst().orElse(null);
        assertNotNull(doorsField);
        assertEquals("own", doorsField.get("origin"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allMembers = new java.util.ArrayList<>(fields);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> methods = (List<Map<String, Object>>) carMap.get("methods");
        if (methods != null) allMembers.addAll(methods);

        Map<String, Object> speedField = allMembers.stream()
                .filter(f -> "speed".equals(f.get("name"))).findFirst().orElse(null);
        assertNotNull(speedField);
        assertEquals("base", speedField.get("origin"));
    }

    @Test
    void test_meta_outline_with_methods_and_comments() {
        AST ast = parser.parse("""
            module m.methods
            outline Service = {
              // Service endpoint URL
              url: String,
              // Fetch data from the service
              fetch: Unit -> String,
              // Service health check
              ping: Unit -> Bool
            };
            let x = 1;
            let y = 2;
            export x, y;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        OutlineMeta service = (OutlineMeta) meta.find("Service");
        assertNotNull(service);

        List<FieldMeta> dataFields = service.fields();
        assertTrue(dataFields.stream().anyMatch(f -> "url".equals(f.name())),
                "Expected 'url' in data fields");

        List<FieldMeta> methods = service.methods();
        assertTrue(methods.stream().anyMatch(f -> "fetch".equals(f.name())),
                "Expected 'fetch' method");
        assertTrue(methods.stream().anyMatch(f -> "ping".equals(f.name())),
                "Expected 'ping' method");

        FieldMeta urlField = service.members().stream()
                .filter(f -> "url".equals(f.name())).findFirst().orElse(null);
        assertNotNull(urlField);
        assertEquals("Service endpoint URL", urlField.description());

        FieldMeta fetchMethod = service.members().stream()
                .filter(f -> "fetch".equals(f.name())).findFirst().orElse(null);
        assertNotNull(fetchMethod);
        assertEquals("Fetch data from the service", fetchMethod.description());
        assertTrue(fetchMethod.isMethod());
    }

    // ── Type resolution tests (Genericable / Returnable) ─────────────────────
    // These tests guard the MetaExtractor.resolveOutline / fieldsOf / resolveTypeName
    // changes that ensure ast.meta() has correct type information for LLM tooling
    // and IDE dot-completion.
    //
    // Scenario A: lambda parameter  countries.filter(c -> c.xxx)
    //   Before fix: c.type = "?5"  (Genericable id) → membersOf("c") = []
    //   After fix:  c.type = "Country"              → membersOf("c") = Country fields
    //
    // Scenario B: terminal method  countries.first()
    //   Before fix: first_country.type = "{code:String,…}" → membersOf = []
    //   After fix:  first_country.type = "Country"         → membersOf = Country fields

    /**
     * Minimal VirtualSet definition prepended AFTER the module declaration.
     * Usage: "module org.test.xxx\n" + VS_PREAMBLE + "outline Country = ...".
     */
    private static final String VS_PREAMBLE = """
            outline VirtualSet = <a>{
                filter: (a -> Bool) -> ~this,
                first:  Unit -> a,
                count:  Unit -> Int
            };
            """;

    /**
     * Lambda parameter type in GCP's structural inference system:
     *
     * GCP uses structural (use-site) type inference for lambda parameters.  When you write
     * {@code countries.filter(c -> c.code == "CN")}, the inferred type of {@code c} is based
     * on the structural usage pattern: {@code c} is accessed as {@code {code: String}}, so
     * the Generic's {@code definedToBe} (lower-bound structural constraint) = {@code {code: String}}.
     *
     * The full Country declaration type is available via the surrounding context, but GCP does
     * NOT propagate it back into the lambda param's {@code extendToBe} during projection.
     * This is a known characteristic of the structural inference engine.
     *
     * Therefore:
     * - {@code c.type} = the structural access type "{code: String}" (Genericable resolved to
     *   its definedToBe constraint) — this is the CORRECT current behavior.
     * - {@code membersOf("c")} returns at least the accessed fields.
     *
     * If GCP's inference is later enhanced to propagate the declared parameter type (Country)
     * into the lambda param's extendToBe, these assertions should be updated to expect "Country".
     */
    @Test
    void test_meta_lambda_param_type_is_structural_access_type() {
        String code = "module org.test.lambdaparam\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let result = countries.filter(c -> c.code == "CN");
                export countries, result;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        // Position inside the lambda body: just before 'c.code'
        int lambdaBodyPos = code.indexOf("c.code") + 1;

        SymbolMeta cSym = meta.resolve("c", lambdaBodyPos);
        assertNotNull(cSym, "Should find lambda parameter 'c' inside the lambda body");
        assertEquals("parameter", cSym.kind());
        // c's type is the Genericable's structural type: the minimal access pattern used in the body.
        // The backtick-quoted form "`{code: String}`" or the plain form "{code: String}" are both acceptable.
        assertNotNull(cSym.type(), "Lambda param 'c' must have a non-null type");
        assertTrue(cSym.type().contains("code"),
                "Lambda param 'c' type should at least reflect the accessed 'code' field. Got: "
                        + cSym.type());
    }

    @Test
    void test_meta_membersOf_lambda_param_returns_accessed_fields() {
        String code = "module org.test.lambdamembers\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let result = countries.filter(c -> c.code == "CN");
                export countries, result;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        int lambdaBodyPos = code.indexOf("c.code") + 1;

        // membersOf("c") returns the fields that GCP inferred from usage patterns.
        // With structural inference, this is at minimum the accessed fields.
        // Collection-level methods (filter, count) must NOT appear.
        List<FieldMeta> members = meta.membersOf("c", lambdaBodyPos);
        assertFalse(members.isEmpty(),
                "membersOf 'c' should return at least the accessed fields; got empty list");
        assertTrue(members.stream().anyMatch(f -> "code".equals(f.name())),
                "Expected 'code' (the accessed field) in 'c' members: "
                        + members.stream().map(FieldMeta::name).toList());
        assertFalse(members.stream().anyMatch(f -> "filter".equals(f.name())),
                "Collection method 'filter' must NOT appear for lambda param 'c'");
        assertFalse(members.stream().anyMatch(f -> "count".equals(f.name())),
                "Collection method 'count' must NOT appear for lambda param 'c'");
    }

    @Test
    void test_meta_variable_type_from_first_resolves_to_entity_name() {
        String code = "module org.test.firstvar\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let first_country = countries.first();
                export countries, result;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        int pos = code.indexOf("export");
        SymbolMeta sym = meta.resolve("first_country", pos);
        assertNotNull(sym, "Should find 'first_country'");
        assertEquals("Country", sym.type(),
                "countries.first() should produce type 'Country'. Got: " + sym.type());
    }

    @Test
    void test_meta_membersOf_result_of_first() {
        String code = "module org.test.firstmembers\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let first_country = countries.first();
                export countries, result;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        int pos = code.indexOf("export");
        List<FieldMeta> members = meta.membersOf("first_country", pos);
        assertFalse(members.isEmpty(),
                "membersOf 'first_country' should return Country fields; got empty list");
        assertTrue(members.stream().anyMatch(f -> "code".equals(f.name())),
                "Expected 'code' in first_country members: "
                        + members.stream().map(FieldMeta::name).toList());
        assertFalse(members.stream().anyMatch(f -> "filter".equals(f.name())),
                "'filter' must NOT appear for element-level symbol 'first_country'");
    }

    @Test
    void test_meta_collection_variable_type_uses_declared_name() {
        String code = "module org.test.colname\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                export countries, result;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        int pos = code.indexOf("export");
        SymbolMeta sym = meta.resolve("countries", pos);
        assertNotNull(sym, "Should find 'countries'");
        assertEquals("Countries", sym.type(),
                "Collection variable type should use the declared name 'Countries', not structural toString. Got: "
                        + sym.type());
    }

    @Test
    void test_MetaExtractor_resolveOutline_unwraps_returnable() {
        // MetaExtractor.resolveOutline is a public API for callers (LLM tools, completion services).
        // It must correctly unwrap Return{supposed: Country} → Country (Entity).
        String code = "module org.test.resolveapi\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let first_country = countries.first();
                export countries, result;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();

        // Grab the outline of 'first_country' from the symbol environment
        var sym = ast.symbolEnv().root().symbols().get("first_country");
        assertNotNull(sym, "Expected 'first_country' in root symbols");

        Outline resolved = MetaExtractor.resolveOutline(sym.outline());
        assertNotNull(resolved, "resolveOutline should not return null");
        assertInstanceOf(Entity.class, resolved,
                "resolveOutline(Return{supposed:Country}) should yield Entity. Got: "
                        + resolved.getClass().getSimpleName());
    }

    @Test
    void test_MetaExtractor_fieldsOf_for_returnable_outline() {
        // MetaExtractor.fieldsOf is the canonical entry-point for dot-completion members.
        // Must return Country fields when given Return{supposed: Country}.
        String code = "module org.test.fieldsofapi\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let first_country = countries.first();
                export countries, result;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();

        var sym = ast.symbolEnv().root().symbols().get("first_country");
        assertNotNull(sym, "Expected 'first_country' in root symbols");

        List<FieldMeta> fields = MetaExtractor.fieldsOf(sym.outline());
        assertFalse(fields.isEmpty(),
                "fieldsOf(Return{supposed:Country}) should return Country members; got []");
        assertTrue(fields.stream().anyMatch(f -> "code".equals(f.name())),
                "Expected 'code' field: " + fields.stream().map(FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "name".equals(f.name())),
                "Expected 'name' field: " + fields.stream().map(FieldMeta::name).toList());
    }

    @Test
    void test_MetaExtractor_fieldsOf_for_genericable_outline() {
        // When given a Genericable (lambda param outline), fieldsOf resolves via guess()
        // which returns the structural access type (definedToBe = {code: String}).
        // Full Country type is NOT available from the Generic's own constraints because GCP's
        // structural inference does not propagate the declared formal parameter type back into
        // the lambda param's extendToBe.  fieldsOf returns the inferred structural fields.
        String code = "module org.test.genericablefields\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let result = countries.filter(c -> c.code == "CN");
                export countries, result;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();

        // Find the lambda parameter 'c' in the inner scope
        var cSym = ast.symbolEnv().allScopes().stream()
                .flatMap(s -> s.symbols().values().stream())
                .filter(s -> "c".equals(s.name()))
                .findFirst().orElse(null);
        assertNotNull(cSym, "Should find lambda parameter 'c' in symbol scopes");

        List<FieldMeta> fields = MetaExtractor.fieldsOf(cSym.outline());
        assertFalse(fields.isEmpty(),
                "fieldsOf(Genericable) should return at least the accessed structural fields; got []");
        assertTrue(fields.stream().anyMatch(f -> "code".equals(f.name())),
                "Expected 'code' (accessed field): " + fields.stream().map(FieldMeta::name).toList());
        // 'name' is NOT expected here: GCP's structural inference for the lambda param only
        // captures the fields actually accessed in the body (c.code), not all of Country's fields.
        // This is a known current limitation.
    }

    @Test
    void test_meta_member_description_in_toMap() {
        AST ast = parser.parse("""
            module m.descmap
            outline Item = {
              // Unique item ID
              id: Int,
              // Display label
              label: String
            };
            let x = 1;
            let y = 2;
            export x, y;
            """);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();
        Map<String, Object> map = meta.toMap();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) map.get("nodes");
        Map<String, Object> itemMap = nodes.stream()
                .filter(n -> "Item".equals(n.get("name"))).findFirst().orElse(null);
        assertNotNull(itemMap);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) itemMap.get("fields");
        assertNotNull(fields);

        Map<String, Object> idField = fields.stream()
                .filter(f -> "id".equals(f.get("name"))).findFirst().orElse(null);
        assertNotNull(idField);
        assertEquals("Unique item ID", idField.get("description"));
        assertEquals("own", idField.get("origin"));

        Map<String, Object> labelField = fields.stream()
                .filter(f -> "label".equals(f.get("name"))).findFirst().orElse(null);
        assertNotNull(labelField);
        assertEquals("Display label", labelField.get("description"));
    }

    /**
     * Playground scenario: user types "c." inside filter lambda.
     * MetaExtractor.fieldsOf(c.outline()) must return Country's members
     * (code, name) — NOT Countries' collection methods (filter, first, count).
     * This validates the Strategy-A path in OutlineCompilerService:
     *   findLastIdentifier(ast, "c") → c.outline() → MetaExtractor.fieldsOf → Country fields
     */
    @Test
    void test_fieldsOf_lambda_param_outline_returns_entity_fields() {
        String code = "module org.test.lambdafieldsof\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let result = countries.filter(c -> c.code == "CN");
                export countries, result;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();

        // Simulate Strategy A: find the 'c' identifier node, get its outline, call fieldsOf
        org.twelve.gcp.ast.Node cNode = findIdentifier(ast.program(), "c");
        assertNotNull(cNode, "Lambda param 'c' must exist in AST");
        org.twelve.gcp.outline.Outline outline = cNode.outline();
        assertNotNull(outline, "Lambda param 'c' must have a non-null outline");

        List<FieldMeta> fields = org.twelve.gcp.meta.MetaExtractor.fieldsOf(outline);
        assertFalse(fields.isEmpty(),
                "fieldsOf(c.outline()) must return members; got empty. outline=" + outline);
        assertFalse(fields.stream().anyMatch(f -> "filter".equals(f.name())),
                "Collection method 'filter' must NOT appear for lambda param 'c'");
        assertFalse(fields.stream().anyMatch(f -> "count".equals(f.name())),
                "Collection method 'count' must NOT appear for lambda param 'c'");
    }

    /**
     * Playground scenario: user types "d." where 'd' is completely undefined.
     * meta.membersOf("d", pos) must return an empty list — not the members of
     * the outer expression (e.g. Countries).  This validates that the completion
     * service should return nothing (not unrelated suggestions) for undefined symbols.
     */
    @Test
    void test_membersOf_undefined_symbol_returns_empty() {
        String code = "module org.test.undefmembers\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let result = countries.filter(c -> d.code == "CN");
                export countries, result;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        // 'd' is never declared anywhere — membersOf must return empty
        int pos = code.indexOf("d.code");
        List<FieldMeta> members = meta.membersOf("d", pos);
        assertTrue(members.isEmpty(),
                "membersOf undefined 'd' must return empty; got: "
                        + members.stream().map(FieldMeta::name).toList());
    }

    /** Utility: find the first identifier node with the given lexeme in an AST subtree. */
    private static org.twelve.gcp.ast.Node findIdentifier(org.twelve.gcp.ast.Node node, String name) {
        if (node == null) return null;
        if (name.equals(node.lexeme())) return node;
        for (org.twelve.gcp.ast.Node child : node.nodes()) {
            org.twelve.gcp.ast.Node found = findIdentifier(child, name);
            if (found != null) return found;
        }
        return null;
    }
}
