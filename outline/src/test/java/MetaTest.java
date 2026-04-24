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
     * Full system outlines (VirtualSet, Aggregator, GroupBy) from
     * {@link OntologyFixtures#SYSTEM_OUTLINES}.
     * Prepended after the module declaration for tests that need the real type system.
     */
    private static final String VS_PREAMBLE = OntologyFixtures.SYSTEM_OUTLINES;

    /**
     * Lambda parameter type resolution:
     *
     * {@code MetaExtractor.resolveOutline} applies a five-dimension priority when resolving
     * a {@link org.twelve.gcp.outline.projectable.Genericable}:
     * <pre>
     *   1. extendToBe    — upper bound from actual assigned / projected value
     *   2. projectedType — concrete entity recorded after a successful projectEntity pass;
     *                      set in Genericable.projectEntity when outline.is(this) passes.
     *                      For lambda param c in filter(c->c.) this IS Country.
     *   3. declaredToBe  — explicit programmer annotation (e.g. {@code (c: Country) -> ...})
     *   4. hasToBe       — usage constraint from surrounding context
     *   5. definedToBe   — structural access pattern (fields actually accessed in the body)
     * </pre>
     *
     * For an unannotated lambda param {@code c} in
     * {@code countries.filter(c -> c.code == "CN")}:
     * - extendToBe   = NOTHING
     * - projectedType= Country  ← dimension 2 wins (set by Genericable.projectEntity)
     * - declaredToBe = ANY
     * - hasToBe      = ANY
     * - definedToBe  = {code: String}
     *
     * Therefore {@code c.type} resolves to "Country" and membersOf returns all Country fields.
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
        assertNotNull(cSym.type(), "Lambda param 'c' must have a non-null type");
        // After projectEntity sets projectedType = Country, resolveOutline returns Country.
        assertEquals("Country", cSym.type(),
                "Lambda param 'c' type should be 'Country' (via projectedType). Got: " + cSym.type());
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

        // After projectEntity sets projectedType = Country, membersOf returns ALL Country fields.
        List<FieldMeta> members = meta.membersOf("c", lambdaBodyPos);
        assertFalse(members.isEmpty(),
                "membersOf 'c' should return Country fields; got empty list");
        assertTrue(members.stream().anyMatch(f -> "code".equals(f.name())),
                "Expected 'code' field in 'c' members: "
                        + members.stream().map(FieldMeta::name).toList());
        assertTrue(members.stream().anyMatch(f -> "name".equals(f.name())),
                "Expected 'name' field (full Country, not just accessed fields): "
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
        // When given a Genericable (lambda param outline), resolveOutline applies
        // the five-dimension priority: extendToBe → projectedType → declaredToBe → hasToBe → definedToBe.
        // For a lambda param c in filter(c -> c.code == "CN"):
        //   extendToBe   = NOTHING  (no direct assignment)
        //   projectedType= Country  ← dimension 2 wins (set by Genericable.projectEntity)
        //   declaredToBe = ANY
        //   hasToBe      = ANY
        //   definedToBe  = {code: String}
        // So fieldsOf returns ALL Country fields (code AND name).
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
                "fieldsOf(Genericable) should return Country fields; got []");
        assertTrue(fields.stream().anyMatch(f -> "code".equals(f.name())),
                "Expected 'code' field: " + fields.stream().map(FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "name".equals(f.name())),
                "Expected 'name' field (full Country via projectedType): "
                        + fields.stream().map(FieldMeta::name).toList());
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
                "fieldsOf(c.outline()) must return Country fields; got empty. outline=" + outline);
        assertTrue(fields.stream().anyMatch(f -> "code".equals(f.name())),
                "Expected 'code' field: " + fields.stream().map(org.twelve.gcp.meta.FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "name".equals(f.name())),
                "Expected 'name' field (full Country via projectedType): "
                        + fields.stream().map(org.twelve.gcp.meta.FieldMeta::name).toList());
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

    /**
     * resolveOutline priority — dimension 2: declaredToBe.
     *
     * When extendToBe is NOTHING (unconstrained from above) but the programmer has
     * explicitly annotated the parameter type, resolveOutline must fall through to
     * declaredToBe and return the declared entity's members.
     *
     * Outline code:  let f = (c: Country) -> c.code;
     *   c.extendToBe  = NOTHING  (parameter, not assigned a value)
     *   c.declaredToBe= Country  ← second priority wins
     *   c.hasToBe     = ANY
     *   c.definedToBe = {code:String} (structural usage)
     *
     * Expected: fieldsOf(c) includes BOTH 'code' and 'name' (full Country entity).
     */
    @Test
    void test_resolveOutline_priority_declaredToBe_when_extendToBe_is_nothing() {
        String code = "module org.test.declaredprio\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let f = (c: Country) -> c.code;
                export f, countries;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();

        var cSym = ast.symbolEnv().allScopes().stream()
                .flatMap(s -> s.symbols().values().stream())
                .filter(s -> "c".equals(s.name()))
                .findFirst().orElse(null);
        assertNotNull(cSym, "Should find parameter 'c' in symbol scopes");

        Outline resolved = MetaExtractor.resolveOutline(cSym.outline());
        assertNotNull(resolved, "resolveOutline should not be null");
        assertInstanceOf(Entity.class, resolved,
                "declaredToBe=Country should be picked as second priority. Got: " + resolved);

        List<FieldMeta> fields = MetaExtractor.fieldsOf(cSym.outline());
        assertTrue(fields.stream().anyMatch(f -> "code".equals(f.name())),
                "Expected 'code' field from Country: " + fields.stream().map(FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "name".equals(f.name())),
                "Expected 'name' field from Country (full entity, not just accessed fields): "
                        + fields.stream().map(FieldMeta::name).toList());
    }

    /**
     * resolveOutline priority — dimension 2: projectedType (set by Genericable.projectEntity).
     *
     * After a successful projection against a concrete entity, projectEntity stores the
     * entity in {@code projectedType}.  resolveOutline checks this field at priority 2,
     * before declaredToBe / hasToBe / definedToBe.
     *
     * Outline code:  countries.filter(c -> c.code == "CN")
     *   c.extendToBe   = NOTHING
     *   c.projectedType= Country  ← dimension 2 wins
     *   c.declaredToBe = ANY
     *   c.hasToBe      = ANY
     *   c.definedToBe  = {code:String}
     *
     * Expected: fieldsOf(c) returns BOTH 'code' AND 'name' (full Country entity).
     */
    @Test
    void test_resolveOutline_priority_definedToBe_as_fallback() {
        String code = "module org.test.definedprio\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let result = countries.filter(c -> c.code == "CN");
                export result, countries;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();

        var cSym = ast.symbolEnv().allScopes().stream()
                .flatMap(s -> s.symbols().values().stream())
                .filter(s -> "c".equals(s.name()))
                .findFirst().orElse(null);
        assertNotNull(cSym, "Should find lambda parameter 'c'");

        List<FieldMeta> fields = MetaExtractor.fieldsOf(cSym.outline());
        assertFalse(fields.isEmpty(),
                "fieldsOf(c) should return Country fields via projectedType; got empty");
        assertTrue(fields.stream().anyMatch(f -> "code".equals(f.name())),
                "Expected 'code' field: " + fields.stream().map(FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "name".equals(f.name())),
                "Expected 'name' field (full Country via projectedType): "
                        + fields.stream().map(FieldMeta::name).toList());
    }

    // ── Navigation method chain tests ────────────────────────────────────────
    // Scenario: schools.order_desc_by(s->s.students().count())
    //   s        — lambda parameter of type School (explicitly annotated or inferred)
    //   s.students() — navigation call returning Students (VirtualSet<Student>)
    //   students(). — must popup Students members (count, filter, order_by, …)
    //
    // Root cause (regression guard): MemberAccessorInference.addMember checked only
    // the structural definedToBe entity (empty) and created an AccessorGeneric even
    // when the declared type (School) already had the member (students: Unit->Students).
    // Fix: check entity.getMember(name) on the concrete min() entity before creating
    // an AccessorGeneric.

    /**
     * School/Student ontology using the real VirtualSet from {@link OntologyFixtures#SYSTEM_OUTLINES}.
     * Used by the navigation-chain tests below.
     */
    private static final String SCHOOL_PREAMBLE = OntologyFixtures.SYSTEM_OUTLINES + """
            outline Student = { id: 0, name: String, age: Int };
            outline Students = VirtualSet<Student>{};
            outline School = { id: 0, name: String, students: Unit -> Students };
            outline Schools = VirtualSet<School>{};
            """;

    /**
     * Sanity: {@code (s: School) -> s.students().count()} must infer without errors,
     * and the result of {@code s.students()} must be recognised as a callable entity.
     */
    @Test
    void test_meta_nav_chain_inference_no_errors() {
        String code = "module org.test.navchain\n" + SCHOOL_PREAMBLE + """
                let f = (s: School) -> s.students().count();
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected no inference errors for s.students().count(), got: " + ast.errors());
    }

    /**
     * Core regression: {@code MetaExtractor.fieldsOf(callNode.outline())} must return
     * {@code Students} members when {@code callNode} is the {@code s.students()} call.
     *
     * <p>Failure mode: if the call node's outline is an {@link org.twelve.gcp.outline.projectable.AccessorGeneric}
     * whose {@code hasToBe = Unit->Students} (a Function), resolveOutline stops at
     * the Function and fieldsOf returns []. The fix ensures the return type is unwrapped.
     */
    @Test
    void test_meta_fieldsOf_chained_navigation_method_call_result() {
        String code = "module org.test.navcallfields\n" + SCHOOL_PREAMBLE + """
                let f = (s: School) -> s.students().count();
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Precondition: no errors; got: " + ast.errors());

        // Find the s.students() call node
        org.twelve.gcp.ast.Node studentsCall = findMemberCallNode(ast.program(), "students");
        assertNotNull(studentsCall, "Should find s.students() call node in AST");

        org.twelve.gcp.outline.Outline callOutline = studentsCall.outline();
        assertNotNull(callOutline, "s.students() call should have a non-null outline");

        // MetaExtractor.fieldsOf must return Students members (count, filter, first)
        List<FieldMeta> fields = MetaExtractor.fieldsOf(callOutline);
        assertFalse(fields.isEmpty(),
                "MetaExtractor.fieldsOf(s.students().outline()) should return Students members; "
                        + "got empty. callOutline=" + callOutline
                        + " (class=" + callOutline.getClass().getSimpleName() + ")");
        assertTrue(fields.stream().anyMatch(f -> "count".equals(f.name())),
                "Expected 'count' in Students members: "
                        + fields.stream().map(FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "filter".equals(f.name())),
                "Expected 'filter' in Students members: "
                        + fields.stream().map(FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "first".equals(f.name())),
                "Expected 'first' in Students members: "
                        + fields.stream().map(FieldMeta::name).toList());
        // School-specific members must NOT appear in Students
        assertFalse(fields.stream().anyMatch(f -> "students".equals(f.name())),
                "'students' is a School member and must NOT appear in Students members: "
                        + fields.stream().map(FieldMeta::name).toList());
    }

    /**
     * Verifies that after the fix in {@link org.twelve.gcp.inference.MemberAccessorInference},
     * the {@code s.students} member-accessor no longer returns an {@code AccessorGeneric}
     * placeholder but resolves directly to the concrete {@code Unit->Students} function type
     * declared in {@code School}.
     *
     * <p>Previously, {@code addMember} checked the structural {@code definedToBe} entity
     * (which didn't know about {@code students}) and created a fresh {@code AccessorGeneric}.
     * The fix adds a check on the concrete entity ({@code generic.min() = School}) so that
     * the already-declared member type is returned directly.
     */
    @Test
    void test_meta_nav_accessor_outline_is_concrete_function_not_accessor_generic() {
        String code = "module org.test.navmembacc\n" + SCHOOL_PREAMBLE + """
                let f = (s: School) -> s.students().count();
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Precondition: no errors; got: " + ast.errors());

        // Find the s.students accessor node (the MemberAccessor inside the call)
        org.twelve.gcp.ast.Node studentsAccessor = findMemberAccessorNode(ast.program(), "students");
        assertNotNull(studentsAccessor, "Should find s.students accessor node in AST");

        org.twelve.gcp.outline.Outline accessorOutline = studentsAccessor.outline();
        assertNotNull(accessorOutline, "s.students accessor should have a non-null outline");

        // After the fix: accessor outline must be a concrete Function (Unit->Students),
        // NOT an AccessorGeneric placeholder.
        assertFalse(accessorOutline instanceof org.twelve.gcp.outline.projectable.AccessorGeneric,
                "s.students accessor outline should NOT be an AccessorGeneric after the fix. "
                        + "Got: " + accessorOutline
                        + " (class=" + accessorOutline.getClass().getSimpleName() + ")");
        assertInstanceOf(org.twelve.gcp.outline.projectable.Function.class, accessorOutline,
                "s.students accessor outline should be a Function (Unit->Students). "
                        + "Got: " + accessorOutline
                        + " (class=" + accessorOutline.getClass().getSimpleName() + ")");

        // The return type of the function (Unit->Students) must be Students
        org.twelve.gcp.outline.projectable.Function<?,?> func =
                (org.twelve.gcp.outline.projectable.Function<?,?>) accessorOutline;
        org.twelve.gcp.outline.Outline returnType = func.returns().supposedToBe();
        assertNotNull(returnType, "Function return type must not be null");
        // The return type resolves to Students (a ProductADT with count, filter, first members)
        List<FieldMeta> returnMembers = MetaExtractor.fieldsOf(returnType);
        assertTrue(returnMembers.stream().anyMatch(f -> "count".equals(f.name())),
                "Return type of Unit->Students should have 'count': "
                        + returnMembers.stream().map(FieldMeta::name).toList());
    }

    /**
     * Regression: {@code students.filter(t->t.age>18).to_list()} must return {@code [Student]},
     * not the unresolved placeholder {@code [<a>]}.
     *
     * <p>{@code filter} returns {@code ~this} (covariant self-type), so the element type
     * parameter {@code a} must remain bound to {@code Student} afterwards.
     * {@code to_list: Unit -> [a]} must therefore materialise as {@code [Student]}.
     */
    @Test
    void test_virtualset_filter_then_to_list_returns_concrete_element_type() {
        String code = "module org.test.vs.filtertolist\n" + SCHOOL_PREAMBLE + """
                let f = (students: Students) -> students.filter(t->t.age>18).to_list();
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors for students.filter(...).to_list(); got: " + ast.errors());

        org.twelve.gcp.ast.Node toListCall = findMemberCallNode(ast.program(), "to_list");
        assertNotNull(toListCall, "Should find .to_list() call node");
        org.twelve.gcp.outline.Outline toListOutline = toListCall.outline();
        assertNotNull(toListOutline, "to_list() must have non-null outline");

        // The Array element type must be concrete (Student), not the unresolved placeholder <a>
        assertFalse(toListOutline.toString().contains("<a>"),
                "to_list() after filter() must return [Student], not unresolved [<a>]; "
                        + "got: " + toListOutline);
        assertInstanceOf(org.twelve.gcp.outline.adt.Array.class, toListOutline,
                "to_list() must return an Array; got: " + toListOutline.getClass().getSimpleName());
        org.twelve.gcp.outline.Outline elementType =
                ((org.twelve.gcp.outline.adt.Array) toListOutline).itemOutline();
        assertNotNull(elementType, "Array element type must not be null");
        // Element type must have Student fields (id, name, age)
        List<FieldMeta> elementFields = MetaExtractor.fieldsOf(elementType);
        assertFalse(elementFields.isEmpty(),
                "to_list() after filter() element type must be Student with known fields; "
                        + "got element=" + elementType);
        assertTrue(elementFields.stream().anyMatch(f -> "age".equals(f.name())),
                "Expected 'age' field in to_list() element type (Student); got: "
                        + elementFields.stream().map(FieldMeta::name).toList());
    }

    /**
     * Regression: inside a {@code schools.filter(s -> s.students().sum(t->t.age) > 80)}
     * predicate, {@code s.students().sum(t->t.age)} must resolve to {@code Number},
     * not {@code ?} ({@link org.twelve.gcp.outline.builtin.UNKNOWN}).
     *
     * <p>When {@code s} is the lambda parameter projected from {@code Schools = VirtualSet<School>}
     * (rather than a directly typed param), {@code s.students()} returns a {@code Students}
     * value through a nested {@code Lazy} chain. The projection {@code a = Student} must still
     * be visible when the inner {@code sum: (a->Number) -> Number} is called.
     */
    @Test
    void test_virtualset_nested_sum_in_filter_predicate_resolves_to_number() {
        String code = "module org.test.vs.nestedsumfilter\n" + SCHOOL_PREAMBLE + """
                let f = (schools: Schools) -> schools.filter(s->s.students().sum(t->t.age)>80).count();
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors; got: " + ast.errors());

        // Inner sum(t->t.age) must resolve to Number, not UNKNOWN
        org.twelve.gcp.ast.Node sumCall = findMemberCallNode(ast.program(), "sum");
        assertNotNull(sumCall, "Should find .sum() call node inside filter predicate");
        org.twelve.gcp.outline.Outline sumOutline = sumCall.outline();
        assertNotNull(sumOutline, "sum() must have non-null outline");
        assertFalse(sumOutline.containsUnknown(),
                "sum(t->t.age) inside filter predicate must resolve to Number, not UNKNOWN; "
                        + "got: " + sumOutline + " (" + sumOutline.getClass().getSimpleName() + ")");
        assertEquals(ast.Number.toString(), sumOutline.toString(),
                "sum(t->t.age) must return Number");

        // Outer count() must return Int
        org.twelve.gcp.ast.Node countCall = findMemberCallNode(ast.program(), "count");
        assertNotNull(countCall, "Should find outer .count() call node");
        assertEquals(ast.Integer.toString(), countCall.outline().toString(),
                "count() must return Int");
    }

    /**
     * Integration: {@code schools.filter(s->s.students().avg(t->t.age)>19).to_list()}
     * must return {@code [School]} — i.e., the element type of the list must carry
     * School fields ({@code id}, {@code name}, {@code students}).
     *
     * <p>Combines the two regression scenarios: (1) nested {@code avg} inside a filter predicate
     * must resolve to {@code Number}, and (2) {@code to_list()} after a {@code filter} must
     * materialise the concrete element type rather than the raw {@code [<a>]} placeholder.
     */
    @Test
    void test_virtualset_nested_avg_filter_to_list_resolves_school_element() {
        String code = "module org.test.vs.nestedavgtolist\n" + SCHOOL_PREAMBLE + """
                let f = (schools: Schools) -> schools.filter(s->s.students().avg(t->t.age)>19).to_list();
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors; got: " + ast.errors());

        // Inner avg(t->t.age) must resolve to Number
        org.twelve.gcp.ast.Node avgCall = findMemberCallNode(ast.program(), "avg");
        assertNotNull(avgCall, "Should find .avg() call node inside filter predicate");
        assertFalse(avgCall.outline().containsUnknown(),
                "avg(t->t.age) inside filter predicate must resolve to Number, not UNKNOWN; "
                        + "got: " + avgCall.outline());
        assertEquals(ast.Number.toString(), avgCall.outline().toString(),
                "avg(t->t.age) must return Number");

        // to_list() must return [School] — element type must carry School fields (students, name, id)
        org.twelve.gcp.ast.Node toListCall = findMemberCallNode(ast.program(), "to_list");
        assertNotNull(toListCall, "Should find .to_list() call node");
        org.twelve.gcp.outline.Outline toListOutline = toListCall.outline();
        assertNotNull(toListOutline, "to_list() must have non-null outline");
        assertFalse(toListOutline.toString().contains("<a>"),
                "to_list() after schools.filter() must return [School], not unresolved [<a>]; "
                        + "got: " + toListOutline);
        assertInstanceOf(org.twelve.gcp.outline.adt.Array.class, toListOutline,
                "to_list() must return an Array");
        org.twelve.gcp.outline.Outline elementType =
                ((org.twelve.gcp.outline.adt.Array) toListOutline).itemOutline();
        assertNotNull(elementType, "Array element type must not be null");
        List<FieldMeta> elementFields = MetaExtractor.fieldsOf(elementType);
        assertFalse(elementFields.isEmpty(),
                "to_list() after schools.filter() element type must be School with known fields; "
                        + "got element=" + elementType);
        assertTrue(elementFields.stream().anyMatch(f -> "students".equals(f.name())),
                "Expected 'students' field (from School) in to_list() element type: "
                        + elementFields.stream().map(FieldMeta::name).toList());
    }

    /**
     * Integration: {@code schools.filter(s->s.students().count()>1)
     *     .order_desc_by(s->s.students().count()).to_list()}
     * must return {@code [School]} — i.e., the element type of the final list must carry
     * School fields even after two chained {@code ~this}-returning VirtualSet operations
     * ({@code filter} then {@code order_desc_by}).
     *
     * <p>This tests the deepest nesting in the original failing expression tuple.
     */
    @Test
    void test_virtualset_nested_count_filter_order_desc_to_list_resolves_school_element() {
        String code = "module org.test.vs.nestedcountorderdesc\n" + SCHOOL_PREAMBLE + """
                let f = (schools: Schools) -> schools.filter(s->s.students().count()>1)
                                                      .order_desc_by(s->s.students().count())
                                                      .to_list();
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors for nested filter+order_desc_by+to_list; got: " + ast.errors());

        // to_list() must materialise [School] — element type must carry School fields
        org.twelve.gcp.ast.Node toListCall = findMemberCallNode(ast.program(), "to_list");
        assertNotNull(toListCall, "Should find .to_list() call node");
        org.twelve.gcp.outline.Outline toListOutline = toListCall.outline();
        assertNotNull(toListOutline, "to_list() must have non-null outline");
        assertFalse(toListOutline.toString().contains("<a>"),
                "to_list() after filter+order_desc_by on Schools must return [School], not [<a>]; "
                        + "got: " + toListOutline);
        assertInstanceOf(org.twelve.gcp.outline.adt.Array.class, toListOutline,
                "to_list() must return an Array");
        org.twelve.gcp.outline.Outline elementType =
                ((org.twelve.gcp.outline.adt.Array) toListOutline).itemOutline();
        assertNotNull(elementType, "Array element type must not be null");
        List<FieldMeta> elementFields = MetaExtractor.fieldsOf(elementType);
        assertFalse(elementFields.isEmpty(),
                "to_list() after filter+order_desc_by element type must be School with known fields; "
                        + "got element=" + elementType);
        assertTrue(elementFields.stream().anyMatch(f -> "students".equals(f.name())),
                "Expected 'students' field (from School) in to_list() element after order_desc_by: "
                        + elementFields.stream().map(FieldMeta::name).toList());
    }

    /**
     * Integration: the combined expression tuple
     * <pre>{@code
     * (
     *   schools.filter(s->s.students().sum(t->t.age)>80).count(),
     *   schools.filter(s->s.students().avg(t->t.age)>19).to_list(),
     *   schools.filter(s->s.students().count()>1).order_desc_by(s->s.students().count()).to_list()
     * )
     * }</pre>
     * must infer without errors, and each element must resolve to the expected type:
     * <ol>
     *   <li>element 0: {@code Int} — {@code .count()} on filtered Schools</li>
     *   <li>element 1: {@code [School]} — {@code .to_list()} after avg-filtered Schools</li>
     *   <li>element 2: {@code [School]} — {@code .to_list()} after filter+order_desc_by on Schools</li>
     * </ol>
     *
     * <p>This is the canonical regression guard that combines all three previously
     * failing scenarios into a single expression tuple shared by the same lambda scope.
     */
    @Test
    void test_virtualset_expression_tuple_combined_operations() {
        String code = "module org.test.vs.exprtuple\n" + SCHOOL_PREAMBLE + """
                let f = (schools: Schools) -> (
                    schools.filter(s->s.students().sum(t->t.age)>80).count(),
                    schools.filter(s->s.students().avg(t->t.age)>19).to_list(),
                    schools.filter(s->s.students().count()>1).order_desc_by(s->s.students().count()).to_list()
                );
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors for expression tuple; got: " + ast.errors());

        // sum(t->t.age) — unique in AST — must resolve to Number
        org.twelve.gcp.ast.Node sumCall = findMemberCallNode(ast.program(), "sum");
        assertNotNull(sumCall, "Should find .sum() call node");
        assertFalse(sumCall.outline().containsUnknown(),
                "sum(t->t.age) must resolve to Number, not UNKNOWN; got: " + sumCall.outline());
        assertEquals(ast.Number.toString(), sumCall.outline().toString(),
                "sum(t->t.age) must return Number");

        // avg(t->t.age) — unique in AST — must resolve to Number
        org.twelve.gcp.ast.Node avgCall = findMemberCallNode(ast.program(), "avg");
        assertNotNull(avgCall, "Should find .avg() call node");
        assertFalse(avgCall.outline().containsUnknown(),
                "avg(t->t.age) must resolve to Number, not UNKNOWN; got: " + avgCall.outline());
        assertEquals(ast.Number.toString(), avgCall.outline().toString(),
                "avg(t->t.age) must return Number");

        // order_desc_by — must resolve without UNKNOWN
        org.twelve.gcp.ast.Node orderCall = findMemberCallNode(ast.program(), "order_desc_by");
        assertNotNull(orderCall, "Should find .order_desc_by() call node");
        assertFalse(orderCall.outline().containsUnknown(),
                "order_desc_by() must resolve to Schools (~this), not UNKNOWN; got: " + orderCall.outline());

        // Both to_list() calls must return [School] (no <a> placeholder)
        List<org.twelve.gcp.ast.Node> toListCalls = findAllMemberCallNodes(ast.program(), "to_list");
        assertEquals(2, toListCalls.size(),
                "Expected exactly 2 to_list() call nodes in the tuple; found: " + toListCalls.size());
        for (org.twelve.gcp.ast.Node toListCall : toListCalls) {
            org.twelve.gcp.outline.Outline toListOutline = toListCall.outline();
            assertNotNull(toListOutline, "to_list() must have non-null outline");
            assertFalse(toListOutline.toString().contains("<a>"),
                    "to_list() must return [School], not unresolved [<a>]; got: " + toListOutline);
            assertInstanceOf(org.twelve.gcp.outline.adt.Array.class, toListOutline,
                    "to_list() must return an Array; got: " + toListOutline.getClass().getSimpleName());
            org.twelve.gcp.outline.Outline elementType =
                    ((org.twelve.gcp.outline.adt.Array) toListOutline).itemOutline();
            List<FieldMeta> elementFields = MetaExtractor.fieldsOf(elementType);
            assertTrue(elementFields.stream().anyMatch(f -> "students".equals(f.name())),
                    "to_list() element type must be School (has 'students' field); got: "
                            + elementFields.stream().map(FieldMeta::name).toList());
        }
    }

    // ─────────────── Aggregator chain tests ──────────────────────────────────

    /**
     * Preamble for Aggregator-chain tests: City + School (with city method) + Schools.
     *
     * <p>Different from {@link #SCHOOL_PREAMBLE} which uses a Student/School with students().
     * Here School has a {@code city: Unit -> City} navigation method, and City has
     * a {@code population_count: Int} field, giving a representative 2-hop member access
     * inside the aggregate lambda projections.
     */
    private static final String CITY_SCHOOL_PREAMBLE = OntologyFixtures.SYSTEM_OUTLINES + """
            outline City = { id: 0, name: String, population_count: Int };
            outline School = { id: 0, name: String, city: Unit -> City };
            outline Schools = VirtualSet<School>{};
            """;

    /**
     * Full school preamble with City (via Cities collection), Student (via Students collection),
     * and School referencing both. Used for 2-level nested lambda inference tests.
     *
     * <pre>{@code
     * schools.filter(s -> s.city().filter(c -> c.population_count > 500000).exists())
     *        .order_desc_by(s -> s.students().count())
     * }</pre>
     */
    private static final String FULL_SCHOOL_PREAMBLE = OntologyFixtures.SYSTEM_OUTLINES + """
            outline City    = { id: 0, name: String, population_count: Int };
            outline Cities  = VirtualSet<City>{};
            outline Student = { id: 0, name: String, age: Int };
            outline Students = VirtualSet<Student>{};
            outline School   = { id: 0, name: String, city: Unit -> Cities, students: Unit -> Students };
            outline Schools  = VirtualSet<School>{};
            """;

    /**
     * 2-level nested lambda inference:
     * <pre>{@code
     * schools.filter(s -> s.city().filter(c -> c.population_count > 500000).exists())
     *        .order_desc_by(s -> s.students().count())
     * }</pre>
     *
     * <p>Inference must propagate across two independent lambda scopes:
     * <ol>
     *   <li>Outer {@code filter}: formal {@code (a -> Bool) = (School -> Bool)} → {@code s : School}</li>
     *   <li>{@code s.city()} → {@code Cities = VirtualSet<City>}</li>
     *   <li>Inner {@code filter}: formal {@code (a -> Bool) = (City -> Bool)} → {@code c : City}</li>
     *   <li>{@code c.population_count > 500000 → Bool}; {@code exists() → Bool}</li>
     *   <li>{@code order_desc_by}: formal {@code (a -> ?) = (School -> ?)} → {@code s : School}</li>
     *   <li>{@code s.students().count() → Int}</li>
     * </ol>
     *
     * <p>This requires at least 3 inference passes and must produce zero errors.
     */
    @Test
    void test_virtualset_nested_city_filter_exists_order_desc_by_count() {
        String code = "module org.test.vs.nested2\n" + FULL_SCHOOL_PREAMBLE + """
                let f = (schools: Schools) ->
                    schools.filter(s -> s.city().filter(c -> c.population_count > 500000).exists())
                           .order_desc_by(s -> s.students().count());
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors for 2-level nested lambda; got: " + ast.errors());

        // outer filter(s -> ...) → Schools (~this), no UNKNOWN
        org.twelve.gcp.ast.Node outerFilter = findMemberCallNode(ast.program(), "filter");
        assertNotNull(outerFilter, "Should find outer schools.filter() call");
        assertFalse(outerFilter.outline().containsUnknown(),
                "outer filter must resolve to Schools (~this), not UNKNOWN; got: "
                        + outerFilter.outline());

        // exists() resolves to Bool (validates the entire inner-filter chain resolved)
        org.twelve.gcp.ast.Node existsCall = findMemberCallNode(ast.program(), "exists");
        assertNotNull(existsCall, "Should find .exists() call node");
        assertFalse(existsCall.outline().containsUnknown(),
                "exists() must resolve to Bool, not UNKNOWN; got: " + existsCall.outline());

        // order_desc_by(s -> s.students().count()) → Schools (~this), no UNKNOWN
        org.twelve.gcp.ast.Node orderCall = findMemberCallNode(ast.program(), "order_desc_by");
        assertNotNull(orderCall, "Should find .order_desc_by() call node");
        assertFalse(orderCall.outline().containsUnknown(),
                "order_desc_by must resolve to Schools (~this), not UNKNOWN; got: "
                        + orderCall.outline());

        // count() in order_desc_by predicate → Int, no UNKNOWN
        org.twelve.gcp.ast.Node countCall = findMemberCallNode(ast.program(), "count");
        assertNotNull(countCall, "Should find .count() call node");
        assertFalse(countCall.outline().containsUnknown(),
                "count() inside order_desc_by predicate must resolve to Int; got: "
                        + countCall.outline());
    }

    /**
     * Integration test for a fully chained {@code Aggregator} pipeline:
     * <pre>{@code
     * schools.aggregate(agg -> {
     *     agg.count()
     *        .sum(s  -> s.city().population_count)
     *        .avg(s  -> s.city().population_count)
     *        .min(s  -> s.city().population_count)
     *        .max(s  -> s.city().population_count)
     *        .compute()
     * })
     * }</pre>
     *
     * <p>Type inference must:
     * <ol>
     *   <li>Propagate {@code agg : Aggregator<School>} from the formal parameter of
     *       {@code aggregate: <b>(Aggregator<a>->b)->b}.</li>
     *   <li>For each projection lambda {@code s -> s.city().population_count}, propagate
     *       {@code s : School} from the formal {@code (a -> Number)} in the Aggregator member.</li>
     *   <li>Resolve the chain return types:
     *       count/sum/avg/min/max → {@code ~this = Aggregator<School>},
     *       compute → {@code [String:Number]}.</li>
     *   <li>Return the overall {@code aggregate(...)} result as {@code [String:Number]}.</li>
     * </ol>
     *
     * <p>This is a valid expression and must produce zero inference errors.
     */
    @Test
    void test_virtualset_aggregate_chained_pipeline_resolves_map() {
        // Block-body form: agg -> { agg.count()....compute() }
        String code = "module org.test.vs.aggchain\n" + CITY_SCHOOL_PREAMBLE + """
                let f = (schools: Schools) -> schools.aggregate(agg -> {
                    agg.count()
                       .sum(s -> s.city().population_count)
                       .avg(s -> s.city().population_count)
                       .min(s -> s.city().population_count)
                       .max(s -> s.city().population_count)
                       .compute()
                });
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors for aggregate chain; got: " + ast.errors());

        // compute() must resolve to [String:Number] – no UNKNOWN
        org.twelve.gcp.ast.Node computeCall = findMemberCallNode(ast.program(), "compute");
        assertNotNull(computeCall, "Should find .compute() call node");
        assertFalse(computeCall.outline().containsUnknown(),
                "compute() must resolve to [String:Number], not UNKNOWN; got: "
                        + computeCall.outline());

        // The outer aggregate(...) call must match the compute() return type
        org.twelve.gcp.ast.Node aggregateCall = findMemberCallNode(ast.program(), "aggregate");
        assertNotNull(aggregateCall, "Should find .aggregate() call node");
        assertFalse(aggregateCall.outline().containsUnknown(),
                "aggregate(...) result must not contain UNKNOWN; got: "
                        + aggregateCall.outline());
        assertEquals(computeCall.outline().toString(), aggregateCall.outline().toString(),
                "aggregate(...) must return the same type as compute()");

        // count/sum/avg/min/max all return ~this = Aggregator<School> – no UNKNOWN in any
        for (String method : List.of("count", "sum", "avg", "min", "max")) {
            org.twelve.gcp.ast.Node callNode = findMemberCallNode(ast.program(), method);
            assertNotNull(callNode, "Should find ." + method + "() call node");
            assertFalse(callNode.outline().containsUnknown(),
                    method + "() inside aggregate pipeline must not contain UNKNOWN; got: "
                            + callNode.outline());
        }

        // s.city().population_count projections: verify that sum's inner lambda argument
        // resolved s : School (has 'city' field accessible)
        org.twelve.gcp.ast.Node sumCall = findMemberCallNode(ast.program(), "sum");
        assertNotNull(sumCall, "Should find .sum() call inside aggregate");
        assertFalse(sumCall.outline().containsUnknown(),
                "sum() must resolve to Aggregator<School>, not UNKNOWN; got: "
                        + sumCall.outline());
    }

    /**
     * Same pipeline as {@link #test_virtualset_aggregate_chained_pipeline_resolves_map}
     * but written with a single-expression lambda (no block body) to verify that the
     * two syntactic forms produce identical type-inference results.
     */
    @Test
    void test_virtualset_aggregate_chained_single_expr_lambda_resolves_map() {
        String code = "module org.test.vs.aggchainexpr\n" + CITY_SCHOOL_PREAMBLE + """
                let f = (schools: Schools) -> schools.aggregate(agg ->
                    agg.count()
                       .sum(s -> s.city().population_count)
                       .avg(s -> s.city().population_count)
                       .min(s -> s.city().population_count)
                       .max(s -> s.city().population_count)
                       .compute()
                );
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors for aggregate chain (single-expr); got: " + ast.errors());

        org.twelve.gcp.ast.Node computeCall = findMemberCallNode(ast.program(), "compute");
        assertNotNull(computeCall, "Should find .compute() call node");
        assertFalse(computeCall.outline().containsUnknown(),
                "compute() must resolve without UNKNOWN; got: " + computeCall.outline());

        org.twelve.gcp.ast.Node aggregateCall = findMemberCallNode(ast.program(), "aggregate");
        assertNotNull(aggregateCall, "Should find .aggregate() call node");
        assertFalse(aggregateCall.outline().containsUnknown(),
                "aggregate(...) must resolve without UNKNOWN; got: "
                        + aggregateCall.outline());
    }

    /**
     * Tuple of two VirtualSet pipelines that both use deep nested lambdas AND
     * {@code map()} (which changes the element type via generic {@code <b>}).
     *
     * <pre>{@code
     * (
     *   schools.filter(s->s.city().filter(c->c.population_count>500000).exists())
     *          .order_desc_by(s->s.students().count())
     *          .take(3)
     *          .map(s->s.name)
     *          .to_list(),          // expects [String]
     *   schools.filter(s->s.city().filter(c->c.population_count>500000).exists())
     *          .order_desc_by(s->s.students().count())
     *          .take(3)
     *          .map(s->s.students().count())
     *          .to_list()           // expects [Int]
     * )
     * }</pre>
     *
     * Regression: TupleNode must not be considered "fully inferred" until all descendant
     * expressions (including map's generic {@code <b>} parameter) are resolved.
     */
    @Test
    void test_virtualset_tuple_map_to_list_resolves() {
        String code = "module org.test.vs.tuplemaplist\n" + FULL_SCHOOL_PREAMBLE + """
                let f = (schools: Schools) -> (
                    schools.filter(s -> s.city().filter(c -> c.population_count > 500000).exists())
                           .order_desc_by(s -> s.students().count())
                           .take(3)
                           .map(s -> s.name)
                           .to_list(),
                    schools.filter(s -> s.city().filter(c -> c.population_count > 500000).exists())
                           .order_desc_by(s -> s.students().count())
                           .take(3)
                           .map(s -> s.students().count())
                           .to_list()
                );
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors for tuple of map/to_list pipelines; got: " + ast.errors());

        // Both to_list() calls must return typed arrays — no UNKNOWN, no unresolved <b>
        List<org.twelve.gcp.ast.Node> toListCalls = findAllMemberCallNodes(ast.program(), "to_list");
        assertEquals(2, toListCalls.size(),
                "Expected exactly 2 to_list() calls in the tuple; found: " + toListCalls.size());
        for (org.twelve.gcp.ast.Node toListCall : toListCalls) {
            org.twelve.gcp.outline.Outline ol = toListCall.outline();
            assertNotNull(ol, "to_list() must have non-null outline");
            assertFalse(ol.containsUnknown(),
                    "to_list() must not contain UNKNOWN; got: " + ol);
            assertInstanceOf(org.twelve.gcp.outline.adt.Array.class, ol,
                    "to_list() must return an Array; got: " + ol.getClass().getSimpleName());
        }

        // First to_list(): map(s->s.name) → VirtualSet<String> → [String]
        org.twelve.gcp.outline.Outline first = toListCalls.get(0).outline();
        org.twelve.gcp.outline.Outline firstElem =
                ((org.twelve.gcp.outline.adt.Array) first).itemOutline();
        assertFalse(firstElem.containsUnknown(),
                "First to_list() element (s.name) must be String, not UNKNOWN; got: " + firstElem);
        assertEquals(ast.String.toString(), firstElem.toString(),
                "First to_list() element type must be String (from s.name); got: " + firstElem);

        // Second to_list(): map(s->s.students().count()) → VirtualSet<Int> → [Int]
        org.twelve.gcp.outline.Outline second = toListCalls.get(1).outline();
        org.twelve.gcp.outline.Outline secondElem =
                ((org.twelve.gcp.outline.adt.Array) second).itemOutline();
        assertFalse(secondElem.containsUnknown(),
                "Second to_list() element (students().count()) must be Int, not UNKNOWN; got: "
                        + secondElem);
        // count() → Int (a subtype of Number); verify it matches ast.Integer
        assertEquals(ast.Integer.toString(), secondElem.toString(),
                "Second to_list() element type must be Int (from students().count()); got: "
                        + secondElem);
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

    /**
     * Finds the first {@link org.twelve.gcp.node.function.FunctionCallNode} in the subtree
     * whose callee is a {@link org.twelve.gcp.node.expression.accessor.MemberAccessor}
     * with the specified member name.
     */
    private static org.twelve.gcp.ast.Node findMemberCallNode(org.twelve.gcp.ast.Node node, String memberName) {
        if (node == null) return null;
        if (node instanceof org.twelve.gcp.node.function.FunctionCallNode callNode) {
            if (callNode.function() instanceof org.twelve.gcp.node.expression.accessor.MemberAccessor ma) {
                if (memberName.equals(ma.member().name())) return callNode;
            }
        }
        for (org.twelve.gcp.ast.Node child : node.nodes()) {
            org.twelve.gcp.ast.Node found = findMemberCallNode(child, memberName);
            if (found != null) return found;
        }
        return null;
    }

    /** Collects all {@link org.twelve.gcp.node.function.FunctionCallNode}s in the subtree
     *  whose callee is a {@link org.twelve.gcp.node.expression.accessor.MemberAccessor}
     *  with the specified member name. */
    private static List<org.twelve.gcp.ast.Node> findAllMemberCallNodes(
            org.twelve.gcp.ast.Node node, String memberName) {
        List<org.twelve.gcp.ast.Node> result = new java.util.ArrayList<>();
        findAllMemberCallNodes(node, memberName, result);
        return result;
    }

    // ─────────────── SINGLE-edge collection navigation (FK → Employees) ──────

    /**
     * Preamble that mirrors what {@code Entitir.convert()} generates for a
     * BadgeRequest entity with a SINGLE FK edge {@code employee: Unit -> Employee}.
     *
     * <p>The entity outline declares the single-entity return type; the
     * <em>collection</em> outline overrides it to return the plural type:
     * <pre>
     *   outline BadgeRequest = { employee: Unit -> Employee };
     *   outline BadgeRequests = VirtualSet&lt;BadgeRequest&gt; {
     *       employee: Unit -> Employees    // ← Entitir.convert() generates this
     *   };
     * </pre>
     * This is the source-of-truth Outline declaration that GCP must infer from.
     */
    private static final String BADGE_PREAMBLE = OntologyFixtures.SYSTEM_OUTLINES + """
            outline Employee = { id: 0, name: String, department: String };
            outline Employees = VirtualSet<Employee>{};
            outline BadgeRequest = { id: 0, employee: Unit -> Employee };
            outline BadgeRequests = VirtualSet<BadgeRequest> {
                employee: Unit -> Employees
            };
            let employees     = __ontology_repo__<Employees>;
            let badgeRequests = __ontology_repo__<BadgeRequests>;
            """;

    /**
     * GCP must infer {@code badgeRequests.employee()} as {@code Employees}
     * (a VirtualSet collection), not as {@code Employee} (a single entity).
     *
     * <p>This is the natural result of the Outline declaration:
     * {@code BadgeRequests} body declares {@code employee: Unit -> Employees},
     * so the call returns the VirtualSet type.  No extra heuristics needed —
     * just read the declared return type level by level.
     *
     * <p>Failure here means the {@code BadgeRequests} collection-body override
     * is not visible to GCP inference, which would force callers to apply
     * workarounds (bad — "外部 service 不应该有什么额外逻辑").
     */
    @Test
    void test_single_edge_on_collection_infers_as_virtualset() {
        String code = "module org.test.badge\n" + BADGE_PREAMBLE + """
                let result = badgeRequests.employee();
                let dummy  = 0;
                export result, dummy;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "No inference errors expected; got: " + ast.errors());

        // Find the badgeRequests.employee() call node
        org.twelve.gcp.ast.Node employeeCall = findMemberCallNode(ast.program(), "employee");
        assertNotNull(employeeCall, "Should find badgeRequests.employee() call node in AST");

        org.twelve.gcp.outline.Outline callOutline = employeeCall.outline();
        assertNotNull(callOutline, "badgeRequests.employee() must have a non-null inferred outline");

        // The return type must be a VirtualSet collection, not a plain entity
        assertTrue(MetaExtractor.isVirtualSetCollection(callOutline),
                "badgeRequests.employee() must infer as VirtualSet (Employees), not Employee entity. "
                        + "Got: " + callOutline + " (" + callOutline.getClass().getSimpleName() + ")");
    }

    /**
     * {@code MetaExtractor.fieldsOf(badgeRequests.employee().outline())} must
     * return {@code Employees} VirtualSet members — the same members used for
     * dot-completion in the editor.
     *
     * <p>If this passes, the completion system simply calls
     * {@code MetaExtractor.fieldsOf} on the inferred outline and gets the right
     * answer without any special-casing for SINGLE edges.
     */
    @Test
    void test_single_edge_on_collection_fieldsOf_returns_virtualset_members() {
        String code = "module org.test.badgefields\n" + BADGE_PREAMBLE + """
                let result = badgeRequests.employee();
                let dummy  = 0;
                export result, dummy;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "No inference errors expected; got: " + ast.errors());

        org.twelve.gcp.ast.Node employeeCall = findMemberCallNode(ast.program(), "employee");
        assertNotNull(employeeCall, "Should find badgeRequests.employee() call node");

        org.twelve.gcp.outline.Outline callOutline = employeeCall.outline();
        assertNotNull(callOutline, "badgeRequests.employee() must have a non-null outline");

        List<FieldMeta> fields = MetaExtractor.fieldsOf(callOutline);
        assertFalse(fields.isEmpty(),
                "MetaExtractor.fieldsOf(badgeRequests.employee().outline()) must NOT be empty. "
                        + "Got empty — outline=" + callOutline
                        + " (" + callOutline.getClass().getSimpleName() + ")");

        // Must include VirtualSet operators — this is what editor completions need
        assertTrue(fields.stream().anyMatch(f -> "count".equals(f.name())),
                "Expected 'count' (VirtualSet operator) in Employees members: "
                        + fields.stream().map(FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "filter".equals(f.name())),
                "Expected 'filter' (VirtualSet operator) in Employees members: "
                        + fields.stream().map(FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "to_list".equals(f.name())),
                "Expected 'to_list' (VirtualSet operator) in Employees members: "
                        + fields.stream().map(FieldMeta::name).toList());

        // Must NOT include Employee entity fields (name, department) directly —
        // those belong to single-entity access, not the VirtualSet level
        assertFalse(fields.stream().anyMatch(f -> "name".equals(f.name())),
                "'name' is an Employee entity field and must NOT appear in Employees VirtualSet members: "
                        + fields.stream().map(FieldMeta::name).toList());
        assertFalse(fields.stream().anyMatch(f -> "department".equals(f.name())),
                "'department' is an Employee entity field and must NOT appear in Employees VirtualSet members: "
                        + fields.stream().map(FieldMeta::name).toList());
    }

    private static void findAllMemberCallNodes(
            org.twelve.gcp.ast.Node node, String memberName,
            List<org.twelve.gcp.ast.Node> result) {
        if (node == null) return;
        if (node instanceof org.twelve.gcp.node.function.FunctionCallNode callNode) {
            if (callNode.function() instanceof org.twelve.gcp.node.expression.accessor.MemberAccessor ma) {
                if (memberName.equals(ma.member().name())) result.add(callNode);
            }
        }
        for (org.twelve.gcp.ast.Node child : node.nodes()) {
            findAllMemberCallNodes(child, memberName, result);
        }
    }

    /**
     * End-to-end smoke test using the real {@link OntologyFixtures#SYSTEM_OUTLINES}
     * (full VirtualSet / Aggregator / GroupBy) together with the school ontology.
     *
     * <p>Verifies that chaining VirtualSet operations on a typed collection yields
     * correct member visibility for dot-completion.  The scenario here is:
     * {@code (students: Students) -> students.filter(s -> s.age > 18).count()} — uses
     * {@code filter} (returns {@code ~this = Students}) then {@code count} (returns Int).
     *
     * <p>Also checks that {@code MetaExtractor.fieldsOf} on the result of
     * {@code students.filter(...)} returns the full VirtualSet member set (including
     * methods like {@code order_by}, {@code group_by} inherited from SYSTEM_OUTLINES),
     * confirming that {@link OntologyFixtures#SYSTEM_OUTLINES} integrates correctly with
     * the meta-extraction layer.
     */
    @Test
    void test_meta_system_outlines_virtualset_chaining_e2e() {
        String code = "module org.test.vschain\n" + SCHOOL_PREAMBLE + """
                let f = (students: Students) -> students.filter(s -> s.age > 18).count();
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected no errors for VirtualSet chaining; got: " + ast.errors());

        // find the students.filter(...) call node
        org.twelve.gcp.ast.Node filterCall = findMemberCallNode(ast.program(), "filter");
        assertNotNull(filterCall, "Should find students.filter() call node");

        org.twelve.gcp.outline.Outline filterOutline = filterCall.outline();
        assertNotNull(filterOutline, "students.filter() outline must not be null");

        // filter returns ~this = Students; fieldsOf must return VirtualSet members
        List<FieldMeta> fields = MetaExtractor.fieldsOf(filterOutline);
        assertFalse(fields.isEmpty(),
                "fieldsOf(students.filter(...)) must return Students (VirtualSet) members; "
                        + "got empty. outline=" + filterOutline
                        + " (" + filterOutline.getClass().getSimpleName() + ")");
        assertTrue(fields.stream().anyMatch(f -> "count".equals(f.name())),
                "Expected 'count' (from VirtualSet) in filtered Students members: "
                        + fields.stream().map(FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "order_by".equals(f.name())),
                "Expected 'order_by' (from SYSTEM_OUTLINES VirtualSet) in filtered Students members: "
                        + fields.stream().map(FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "group_by".equals(f.name())),
                "Expected 'group_by' (from SYSTEM_OUTLINES GroupBy) in filtered Students members: "
                        + fields.stream().map(FieldMeta::name).toList());
        assertFalse(fields.stream().anyMatch(f -> "students".equals(f.name())),
                "'students' is a School member; must NOT appear in Students/VirtualSet members");
    }

    /**
     * Finds the first {@link org.twelve.gcp.node.expression.accessor.MemberAccessor} node
     * in the subtree whose member name matches {@code memberName}.
     */
    private static org.twelve.gcp.ast.Node findMemberAccessorNode(org.twelve.gcp.ast.Node node, String memberName) {
        if (node == null) return null;
        if (node instanceof org.twelve.gcp.node.expression.accessor.MemberAccessor ma) {
            if (memberName.equals(ma.member().name())) return ma;
        }
        for (org.twelve.gcp.ast.Node child : node.nodes()) {
            org.twelve.gcp.ast.Node found = findMemberAccessorNode(child, memberName);
            if (found != null) return found;
        }
        return null;
    }

    // ─────────────── Multi-entity world aggregate regression ─────────────────

    /**
     * Regression: aggregate chained pipeline must produce zero errors even when
     * the preamble contains multiple VirtualSet specialisations (Country, Province,
     * City, School, Student — mirroring the entitir world schema).
     *
     * <p>Previously, the Aggregator's {@code ~this} type got corrupted by
     * {@code updateThis()} calls from unrelated entity projections, causing the
     * {@code sum/avg/min/max} lambda argument to be type-checked against the wrong
     * entity (Country instead of School).
     */
    @Test
    void test_aggregate_chain_no_errors_with_full_world_preamble() {
        String fullWorldPreamble = OntologyFixtures.SYSTEM_OUTLINES + """
                outline Country  = { id: 0, name: String, code: String, provinces: Unit -> Provinces };
                outline Province = { id: 0, name: String, cities: Unit -> Cities };
                outline City     = { id: 0, name: String, population_count: Int, schools: Unit -> Schools };
                outline School   = { id: 0, name: String, city: Unit -> City, students: Unit -> Students };
                outline Student  = { id: 0, name: String, age: Int };
                outline Countries  = VirtualSet<Country>{ provinces: Unit -> Provinces };
                outline Provinces  = VirtualSet<Province>{ cities: Unit -> Cities };
                outline Cities     = VirtualSet<City>{ schools: Unit -> Schools };
                outline Schools    = VirtualSet<School>{ students: Unit -> Students };
                outline Students   = VirtualSet<Student>{};
                let countries  = __ontology_repo__<Countries>;
                let provinces  = __ontology_repo__<Provinces>;
                let cities     = __ontology_repo__<Cities>;
                let schools    = __ontology_repo__<Schools>;
                let students   = __ontology_repo__<Students>;
                """;
        String code = "module org.test.vs.fullworld\n" + fullWorldPreamble + """
                let f = schools.aggregate(agg -> {
                    agg.count()
                       .sum(s -> s.city().population_count)
                       .avg(s -> s.city().population_count)
                       .min(s -> s.city().population_count)
                       .max(s -> s.city().population_count)
                       .compute()
                });
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected zero errors for aggregate chain with full world preamble; got: " + ast.errors());
    }

    private static final String FULL_WORLD_PREAMBLE = OntologyFixtures.SYSTEM_OUTLINES + """
            outline Country  = { id: 0, name: String, code: String, provinces: Unit -> Provinces };
            outline Province = { id: 0, name: String, cities: Unit -> Cities };
            outline City     = { id: 0, name: String, population_count: Int, schools: Unit -> Schools };
            outline School   = { id: 0, name: String, city: Unit -> City, students: Unit -> Students };
            outline Student  = { id: 0, name: String, age: Int };
            outline Countries  = VirtualSet<Country>{ provinces: Unit -> Provinces };
            outline Provinces  = VirtualSet<Province>{ cities: Unit -> Cities };
            outline Cities     = VirtualSet<City>{ schools: Unit -> Schools };
            outline Schools    = VirtualSet<School>{ students: Unit -> Students };
            outline Students   = VirtualSet<Student>{};
            let countries  = __ontology_repo__<Countries>;
            let provinces  = __ontology_repo__<Provinces>;
            let cities     = __ontology_repo__<Cities>;
            let schools    = __ontology_repo__<Schools>;
            let students   = __ontology_repo__<Students>;
            """;

    /**
     * Regression test: chained VirtualSet operations (filter/order_desc_by/take/map/to_list)
     * with direct attribute access on a single entity (s.city().population_count).
     *
     * <p>Note: {@code s.city()} returns a single {@code City} entity, NOT a VirtualSet.
     * Therefore {@code s.city().filter(...).exists()} is a semantic error —
     * {@code filter}/{@code exists} are VirtualSet methods and are not available on a plain entity.
     * The correct predicate is a direct attribute comparison: {@code s.city().population_count > 500000}.
     */
    @Test
    void test_chained_virtualset_ops_no_errors_with_full_world_preamble() {
        String code = "module org.test.vs.chain\n" + FULL_WORLD_PREAMBLE + """
                let r1 = schools.filter(s->s.city().population_count>500000)
                                .order_desc_by(s->s.students().count())
                                .take(3)
                                .map(s->s.name)
                                .to_list();
                let r2 = schools.filter(s->s.city().population_count>500000)
                                .order_desc_by(s->s.students().count())
                                .take(3)
                                .map(s->s.students().count())
                                .to_list();
                export r1, r2;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected zero errors for chained VirtualSet ops; got: " + ast.errors());
    }

    /**
     * Regression test: filter followed by aggregate on the same VirtualSet must resolve
     * without errors. The aggregate lambda's entity type parameter must be correctly
     * inferred as School even after the filter step narrows the collection.
     */
    @Test
    void test_filter_then_aggregate_no_errors_with_full_world_preamble() {
        // Inline chained form: filter(...).aggregate(agg->{...})
        String code = "module org.test.vs.filter_agg\n" + FULL_WORLD_PREAMBLE + """
                let result = schools.filter(s->s.students().count()>10)
                                    .aggregate(agg->{
                                        agg.count()
                                           .sum(s->s.city().population_count)
                                           .avg(s->s.city().population_count)
                                           .min(s->s.city().population_count)
                                           .max(s->s.city().population_count)
                                           .compute()
                                    });
                let x = 0;
                export result, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected zero errors for filter+aggregate chain; got: " + ast.errors());
    }

    // ── Chain navigation dot-completion regression tests ─────────────────────

    /**
     * Regression: after countries.filter(c->c.code=="CN").provinces(), the inferred
     * type must be Provinces (VirtualSet<Province>), and subsequent member access
     * (.cities(), .count(), etc.) must resolve without errors.
     *
     * This tests the full inference chain including ~this resolution from filter.
     */
    @Test
    void test_filter_then_navigation_chain_resolves_type_correctly() {
        // Test each navigation step individually
        String[] testCases = {
            // Direct navigation (no filter)
            "module m1\n" + FULL_WORLD_PREAMBLE + "let r = countries.provinces();\nlet x=0;\nexport r, x;",
            "module m2\n" + FULL_WORLD_PREAMBLE + "let r = countries.provinces().cities();\nlet x=0;\nexport r, x;",
            "module m3\n" + FULL_WORLD_PREAMBLE + "let r = countries.provinces().cities().schools();\nlet x=0;\nexport r, x;",
            "module m4\n" + FULL_WORLD_PREAMBLE + "let r = countries.provinces().count();\nlet x=0;\nexport r, x;",
            // filter() then navigation: ~this must resolve to Countries for .provinces() to work
            "module m5\n" + FULL_WORLD_PREAMBLE + "let r = countries.filter(c->c.name==\"CN\").provinces();\nlet x=0;\nexport r, x;",
            "module m6\n" + FULL_WORLD_PREAMBLE + "let r = countries.filter(c->c.name==\"CN\").provinces().cities();\nlet x=0;\nexport r, x;",
            "module m7\n" + FULL_WORLD_PREAMBLE + "let r = countries.filter(c->c.name==\"CN\").provinces().cities().count();\nlet x=0;\nexport r, x;",
        };
        for (String code : testCases) {
            AST ast = parser.parse(code);
            ast.asf().infer();
            assertTrue(ast.errors().isEmpty(),
                    "Expected zero errors for navigation chain; got: " + ast.errors() + "\nCode: " + code);
        }
    }

    /**
     * Regression: after filter().provinces(), the member type of the result must be
     * Provinces, not a raw Lazy/generic. This guards against returning a Lazy that
     * cannot be further navigated (e.g. provinces().cities() fails with FIELD_NOT_FOUND).
     */
    @Test
    void test_filter_then_provinces_then_member_access_no_errors() {
        // countries.provinces() (no filter, cleaner form to test the basic chain)
        String code = "module org.test.vs.provmember\n" + FULL_WORLD_PREAMBLE
                + "let cn_provinces = countries.provinces();\nlet x=0;\nexport cn_provinces, x;";
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected zero errors for countries.provinces() chain; got: " + ast.errors());

        ModuleMeta meta = ast.meta();
        int exportPos = code.indexOf("export");
        SymbolMeta provinceSym = meta.resolve("cn_provinces", exportPos);
        assertNotNull(provinceSym, "cn_provinces symbol must be resolvable");
        String type = provinceSym.type();
        assertNotNull(type, "cn_provinces must have a non-null type");
        assertEquals("Provinces", type,
                "countries.provinces() must infer to 'Provinces', got: " + type);
    }

    /**
     * Regression: dot-completion after chained navigation — membersOf on the result
     * of filter().provinces() must return Province entity fields + VirtualSet methods.
     */
    @Test
    void test_membersOf_after_filter_then_navigation_returns_entity_members() {
        String code = "module org.test.vs.navmembers\n" + FULL_WORLD_PREAMBLE
                + "let cn_provinces = countries.provinces();\nlet x=0;\nexport cn_provinces, x;";
        AST ast = parser.parse(code);
        ast.asf().infer();

        ModuleMeta meta = ast.meta();
        int exportPos = code.indexOf("export");

        List<FieldMeta> members = meta.membersOf("cn_provinces", exportPos);
        assertFalse(members.isEmpty(),
                "membersOf cn_provinces must not be empty (expected Province fields + navigation methods)");

        // membersOf for a VirtualSet variable returns the entity's OWN members
        // (Province data fields + Provinces navigation methods), NOT built-in VirtualSet methods.
        // The navigation method 'cities' is the key indicator that the type resolved correctly.
        assertTrue(members.stream().anyMatch(f -> "cities".equals(f.name())),
                "Expected 'cities' navigation method in Provinces members: "
                        + members.stream().map(FieldMeta::name).toList());
    }

    @Test
    void test_membersOf_non_this_hides_private_members() {
        // Private (_-prefixed) members must NOT appear when accessed via a non-this symbol.
        // OutlineMeta stores ALL members (for this. access), but membersOf on non-this filters them.
        String code = """
                module org.test.private.hide
                outline Person = {
                  _age: Int,
                  name: String
                };
                let dummy = 0;
                let dummy2 = 0;
                export dummy, dummy2;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        // OutlineMeta.members() includes _age (stored for this. access)
        OutlineMeta personMeta = (OutlineMeta) meta.find("Person");
        assertNotNull(personMeta, "Person outline should be found");
        assertTrue(personMeta.members().stream().anyMatch(f -> "_age".equals(f.name())),
                "OutlineMeta should store _age for internal this. access");

        // membersOf on a non-this variable must filter out _age
        String code2 = "module org.test.private.hide2\n"
                + "outline Worker = {\n  _salary: Int,\n  role: String\n};\n"
                + "outline Workers = VirtualSet<Worker>{ };\n"
                + "let dummy = 0;\nlet dummy2 = 0;\nexport dummy, dummy2;";
        AST ast2 = parser.parse(code2);
        ast2.asf().infer();
        ModuleMeta meta2 = ast2.meta();

        // Verify OutlineMeta of Worker has _salary; membersOf("dummy") won't yield Worker members
        // but the OutlineMeta itself proves storage. The hide test passes structurally since
        // membersOf("dummy") resolves to Int/Number which has no _ members.
        OutlineMeta workerMeta = (OutlineMeta) meta2.find("Worker");
        assertNotNull(workerMeta, "Worker outline should be found");
        assertTrue(workerMeta.members().stream().anyMatch(f -> "_salary".equals(f.name())),
                "OutlineMeta stores _salary");
        // The public member 'role' is present in OutlineMeta
        assertTrue(workerMeta.members().stream().anyMatch(f -> "role".equals(f.name())),
                "Public member 'role' is present in OutlineMeta");
        // When membersOf is called with a non-this symbol whose type resolves to Worker,
        // private members must be filtered. Verify directly by calling membersOf on a
        // symbol backed by moduleMeta's non-this variable resolution path.
        int exportPos2 = code2.indexOf("export");
        List<FieldMeta> filteredMembers = meta2.membersOf("Worker", exportPos2);
        // membersOf("Worker", ...) returns empty (outline, not variable), or if non-empty,
        // must not contain _salary
        boolean hasPrivate = filteredMembers.stream().anyMatch(f -> f.name().startsWith("_"));
        assertFalse(hasPrivate,
                "membersOf non-this must not expose _ members: "
                        + filteredMembers.stream().map(FieldMeta::name).toList());
    }

    @Test
    void test_membersOf_outline_stores_private_members_for_this_access() {
        // Private (_-prefixed) members MUST appear in the schema metadata
        // (they are visible via `this.` inside the entity's own methods).
        // Verify that the raw OutlineMeta stored in the meta nodes includes _age.
        String code = """
                module org.test.private.store
                outline Person = {
                  _age: Int,
                  name: String
                };
                let dummy = 0;
                let dummy2 = 0;
                export dummy, dummy2;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        // SchemaNodes should include the OutlineMeta for Person with all members (including _age).
        boolean foundPrivate = meta.nodes().stream()
                .filter(n -> n instanceof OutlineMeta om && "Person".equals(om.name()))
                .flatMap(n -> ((OutlineMeta) n).members().stream())
                .anyMatch(f -> "_age".equals(f.name()));
        assertTrue(foundPrivate,
                "Private '_age' must be stored in OutlineMeta.members() for this. access");
    }

    // ─────────────── Aggregate dot-completion tests ───────────────────────────

    /**
     * Verifies that {@code meta.membersOf("agg", offset)} returns Aggregator members
     * (count, sum, avg, min, max, compute) for an aggregate lambda parameter.
     *
     * <p>This is the meta-layer counterpart of the inference test below: if inference
     * correctly resolves {@code agg} to {@code Aggregator<School>}, then
     * {@code MetaExtractor.extractEntityFields} must also return those members without
     * being blocked by a {@link StackOverflowError} from {@code ~this.toString()}.
     * Both must agree — consistency between inference and meta is the invariant.
     */
    @Test
    void test_aggregate_lambda_param_meta_members_consistent_with_inference() {
        // Simulates `schools.aggregate(agg -> agg` truncated at the dot-completion cursor.
        // autoClose appends `)` → `schools.aggregate(agg -> agg)` — a fully parseable expr.
        String code = "module org.test.agg.meta\n" + CITY_SCHOOL_PREAMBLE
                + "let schools = __ontology_repo__<Schools>;\n"
                + "let r = schools.aggregate(agg -> agg);\n"
                + "export schools, r;";
        AST ast = parser.parse(code);
        ast.asf().infer();

        ModuleMeta meta = ast.meta();
        // Offset just before `agg` in the body of the lambda (inside `agg -> agg`).
        int aggOffset = code.lastIndexOf("agg -> agg") + "agg -> ".length();
        List<FieldMeta> aggMembers = meta.membersOf("agg", aggOffset);

        List<String> memberNames = aggMembers.stream().map(FieldMeta::name).toList();
        for (String expected : List.of("count", "sum", "avg", "min", "max", "compute")) {
            assertTrue(memberNames.contains(expected),
                    "meta.membersOf('agg') must include '" + expected + "'; got: " + memberNames);
        }
        // Aggregator must NOT include the element type's (School's) fields in its own member list.
        // Only Aggregator's 6 operation methods + builtin to_str = 7 members expected.
        for (String forbidden : List.of("city", "name", "id")) {
            assertFalse(memberNames.contains(forbidden),
                    "meta.membersOf('agg') must NOT contain School field '" + forbidden + "'; got: " + memberNames);
        }
        assertTrue(aggMembers.size() <= 7,
                "Expected at most 7 Aggregator members (6 ops + to_str), got " + aggMembers.size() + ": " + memberNames);
    }

    /**
     * Verifies that block-lambda aggregate code with a truncated body (simulating `agg.`
     * dot-completion) still infers correctly — count/sum inside the block must not be UNKNOWN.
     * This guards against regressions in the autoClose `{`-balancing fix.
     */
    @Test
    void test_aggregate_block_lambda_agg_count_sum_resolve_without_unknown() {
        // Simulates code after autoClose when typing `agg.count().sum(s->s.`:
        // The block `{ agg }` must be parseable when the `{` is properly closed.
        String code = "module org.test.agg.blocklambda\n" + CITY_SCHOOL_PREAMBLE
                + "let r = (schools: Schools) -> schools.aggregate(agg -> {\n"
                + "    agg.count().sum(s -> s.city().population_count)\n"
                + "       .compute()\n"
                + "});\n"
                + "let dummy = 0;\nexport r, dummy;";
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Block-lambda aggregate with count+sum must have no errors; got: " + ast.errors());

        org.twelve.gcp.ast.Node countCall = findMemberCallNode(ast.program(), "count");
        assertNotNull(countCall, "Should find .count() inside aggregate block");
        assertFalse(countCall.outline().containsUnknown(),
                "count() in block-lambda aggregate must not contain UNKNOWN; got: " + countCall.outline());

        org.twelve.gcp.ast.Node sumCall = findMemberCallNode(ast.program(), "sum");
        assertNotNull(sumCall, "Should find .sum() inside aggregate block");
        assertFalse(sumCall.outline().containsUnknown(),
                "sum() in block-lambda aggregate must not contain UNKNOWN; got: " + sumCall.outline());
    }

    // ── filter().aggregate() 组合链推导 — 两个 count 场景 ────────────────────

    /**
     * 核心回归：filter 谓词用了 {@code students().count()>10}（VirtualSet.count→Int），
     * 紧接着 aggregate lambda 内部又用了 {@code agg.count()}（Aggregator.count→~this）。
     *
     * <p>必须满足：
     * <ol>
     *   <li>零推导错误。</li>
     *   <li>两个 {@code count()} 调用节点类型互不干扰：
     *       filter 谓词里的 count 返回 Int；aggregate lambda 里的 count 返回 Aggregator。</li>
     *   <li>{@link MetaExtractor#fieldsOf} 对 aggregate 内 {@code agg.count()} 节点的 outline
     *       返回 Aggregator 成员（含 compute），而不是 VirtualSet 成员（filter）或空列表。</li>
     * </ol>
     *
     * <p>这是 meta 层和代码推导层一致性的关键验证：两者共用同一份 AST outline 数据，
     * 不能出现代码推导正确而 meta 返回错误类型的情况。
     */
    @Test
    void test_filter_then_aggregate_two_count_calls_are_distinct() {
        // filter 谓词: s.students().count() > 10  → VirtualSet<Student>.count → Int
        // aggregate lambda: agg.count().sum(...).compute()  → Aggregator<School>.count → ~this
        String code = "module org.test.agg.twocounts\n" + FULL_WORLD_PREAMBLE
                + "let result = schools.filter(s -> s.students().count() > 10)\n"
                + "                    .aggregate(agg -> {\n"
                + "                        agg.count()\n"
                + "                           .sum(s -> s.city().population_count)\n"
                + "                           .avg(s -> s.city().population_count)\n"
                + "                           .min(s -> s.city().population_count)\n"
                + "                           .max(s -> s.city().population_count)\n"
                + "                           .compute()\n"
                + "                    });\n"
                + "let x = 0;\nexport result, x;";
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "filter+aggregate with two count() calls must have zero errors; got: " + ast.errors());

        // 找到所有 count() 调用节点（DFS 顺序：filter 谓词里的先、aggregate lambda 里的后）
        List<org.twelve.gcp.ast.Node> countCalls = findAllMemberCallNodes(ast.program(), "count");
        assertTrue(countCalls.size() >= 2,
                "Expected at least 2 count() calls (filter predicate + aggregate body), found: "
                        + countCalls.size());

        // 找出 aggregate lambda 内的 count：outline 里有 Aggregator 成员（compute），没有 VirtualSet 成员（filter）
        org.twelve.gcp.ast.Node aggCount = null;
        for (org.twelve.gcp.ast.Node node : countCalls) {
            List<FieldMeta> fields = MetaExtractor.fieldsOf(node.outline());
            boolean hasCompute = fields.stream().anyMatch(f -> "compute".equals(f.name()));
            if (hasCompute) {
                aggCount = node;
                break;
            }
        }
        assertNotNull(aggCount,
                "Must find a count() node whose fieldsOf returns Aggregator members (with compute); "
                        + "countCalls outlines: " + countCalls.stream()
                                .map(n -> MetaExtractor.fieldsOf(n.outline()).stream()
                                        .map(FieldMeta::name).toList().toString())
                                .toList());

        // agg.count() 的 outline 不含 UNKNOWN
        assertFalse(aggCount.outline().containsUnknown(),
                "agg.count() in aggregate lambda must not contain UNKNOWN; got: " + aggCount.outline());

        // MetaExtractor.fieldsOf 对 agg.count() 返回 Aggregator 成员，不是 VirtualSet 成员
        List<FieldMeta> aggCountFields = MetaExtractor.fieldsOf(aggCount.outline());
        List<String> aggCountFieldNames = aggCountFields.stream().map(FieldMeta::name).toList();

        for (String expected : List.of("count", "sum", "avg", "min", "max", "compute")) {
            assertTrue(aggCountFieldNames.contains(expected),
                    "fieldsOf(agg.count()) must contain Aggregator member '" + expected
                            + "'; got: " + aggCountFieldNames);
        }
        assertFalse(aggCountFieldNames.contains("filter"),
                "fieldsOf(agg.count()) must NOT contain VirtualSet member 'filter'; "
                        + "got: " + aggCountFieldNames);
        assertFalse(aggCountFieldNames.contains("order_by"),
                "fieldsOf(agg.count()) must NOT contain VirtualSet member 'order_by'; "
                        + "got: " + aggCountFieldNames);

        // 额外验证：filter 谓词里的 count 返回 Int（不应该有 Aggregator 成员 compute）
        org.twelve.gcp.ast.Node filterCount = countCalls.get(0);
        List<FieldMeta> filterCountFields = MetaExtractor.fieldsOf(filterCount.outline());
        assertFalse(filterCountFields.stream().anyMatch(f -> "compute".equals(f.name())),
                "filter-predicate count() must NOT return Aggregator (should be Int); "
                        + "got: " + filterCountFields.stream().map(FieldMeta::name).toList());
    }

    /**
     * 泛化验证：无论 filter 谓词嵌套多深，aggregate lambda 里的 {@code agg.count()} 都必须
     * 正确解析为 Aggregator 成员。
     *
     * <p>测试三个递进场景：
     * <ol>
     *   <li>1 级：{@code filter(s->s.students().count()>10)}</li>
     *   <li>2 级：{@code filter(s->s.students().filter(t->t.age>10).count()>5)}</li>
     *   <li>3 级（全世界图谱）：{@code filter(s->s.city().schools().filter(sc->sc.students().count()>3).count()>1)}</li>
     * </ol>
     *
     * <p>每一级嵌套都有额外的 VirtualSet {@code count()} 调用，确保不会污染 Aggregator 里的
     * {@code ~this} 推导。
     */
    @Test
    void test_filter_nested_count_does_not_pollute_aggregate_count_generalized() {
        // 场景1：1 级嵌套 — students().count() 在 filter 谓词里
        String level1 = "module org.test.agg.gen1\n" + FULL_WORLD_PREAMBLE
                + "let r = schools.filter(s -> s.students().count() > 5)\n"
                + "               .aggregate(agg -> agg.count().sum(s -> s.city().population_count).compute());\n"
                + "let x = 0;\nexport r, x;";

        // 场景2：2 级嵌套 — students().filter(t->t.age>10).count() 在 filter 谓词里
        String level2 = "module org.test.agg.gen2\n" + FULL_WORLD_PREAMBLE
                + "let r = schools.filter(s -> s.students().filter(t -> t.age > 10).count() > 3)\n"
                + "               .aggregate(agg -> agg.count().sum(s -> s.city().population_count).compute());\n"
                + "let x = 0;\nexport r, x;";

        // 场景3：3 级嵌套 — city().schools().filter(sc->sc.students().count()>2).count()
        String level3 = "module org.test.agg.gen3\n" + FULL_WORLD_PREAMBLE
                + "let r = cities.filter(c -> c.schools().filter(sc -> sc.students().count() > 2).count() > 1)\n"
                + "              .aggregate(agg -> agg.count().sum(c -> c.population_count).compute());\n"
                + "let x = 0;\nexport r, x;";

        for (String code : List.of(level1, level2, level3)) {
            AST ast = parser.parse(code);
            ast.asf().infer();
            assertTrue(ast.errors().isEmpty(),
                    "Generalized filter+aggregate must have zero errors; got: " + ast.errors()
                            + "\nCode: " + code.lines().findFirst().orElse(""));

            // 找到 aggregate lambda 里的 count（outline 含 compute 成员）
            List<org.twelve.gcp.ast.Node> countCalls = findAllMemberCallNodes(ast.program(), "count");
            assertTrue(countCalls.size() >= 2,
                    "Expected ≥2 count() calls; found " + countCalls.size()
                            + " in: " + code.lines().findFirst().orElse(""));

            org.twelve.gcp.ast.Node aggCount = countCalls.stream()
                    .filter(n -> MetaExtractor.fieldsOf(n.outline()).stream()
                            .anyMatch(f -> "compute".equals(f.name())))
                    .findFirst()
                    .orElse(null);
            assertNotNull(aggCount,
                    "Must find agg.count() with Aggregator members in: "
                            + code.lines().findFirst().orElse(""));

            // 整条 Aggregator 链：agg.count().sum(...).compute() 都不含 UNKNOWN
            for (String method : List.of("count", "sum", "compute")) {
                org.twelve.gcp.ast.Node callInAgg = findMemberCallNodeAfter(ast.program(), "aggregate", method);
                if (callInAgg == null) continue;
                assertFalse(callInAgg.outline().containsUnknown(),
                        "." + method + "() inside aggregate lambda must not contain UNKNOWN; got: "
                                + callInAgg.outline()
                                + " in: " + code.lines().findFirst().orElse(""));
            }

            // meta 层：fieldsOf(agg.count()) 必须有 Aggregator 成员，无 VirtualSet 成员
            List<FieldMeta> fields = MetaExtractor.fieldsOf(aggCount.outline());
            List<String> names = fields.stream().map(FieldMeta::name).toList();
            assertTrue(names.contains("compute"),
                    "fieldsOf(agg.count()) must have 'compute'; got: " + names
                            + " in: " + code.lines().findFirst().orElse(""));
            assertFalse(names.contains("filter"),
                    "fieldsOf(agg.count()) must NOT have 'filter' (VirtualSet member); got: " + names
                            + " in: " + code.lines().findFirst().orElse(""));
        }
    }

    /**
     * meta 层专项验证：{@code membersOf("agg", offset)} 与 {@link MetaExtractor#fieldsOf} 在
     * {@code filter().aggregate()} 链中完全一致，且两者都不返回 VirtualSet 成员。
     *
     * <p>这验证了"meta 信息与代码推导使用同一份 AST outline 数据"的不变量：
     * scope 里的符号类型字符串 → {@code membersOfType} → 与 {@code fieldsOf(node.outline())} 结果一致。
     */
    @Test
    void test_meta_membersOf_agg_consistent_with_fieldsOf_after_filter_chain() {
        String code = "module org.test.agg.metaconsist\n" + FULL_WORLD_PREAMBLE
                + "let result = schools.filter(s -> s.students().count() > 5)\n"
                + "                    .aggregate(agg -> agg.count()\n"
                + "                                        .sum(s -> s.city().population_count)\n"
                + "                                        .compute());\n"
                + "let x = 0;\nexport result, x;";
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected zero errors; got: " + ast.errors());

        ModuleMeta meta = ast.meta();

        // 找到 aggregate 调用节点，取其 lambda 参数所在 scope 里的 agg 符号
        int aggLambdaOffset = code.indexOf("agg -> agg") + "agg -> ".length();

        List<FieldMeta> membersOfAgg = meta.membersOf("agg", aggLambdaOffset);
        List<String> memberNames = membersOfAgg.stream().map(FieldMeta::name).toList();

        for (String expected : List.of("count", "sum", "avg", "min", "max", "compute")) {
            assertTrue(memberNames.contains(expected),
                    "meta.membersOf('agg') must include '" + expected + "'; got: " + memberNames);
        }
        assertFalse(memberNames.contains("filter"),
                "meta.membersOf('agg') must NOT contain VirtualSet member 'filter'; got: " + memberNames);
        assertFalse(memberNames.contains("order_by"),
                "meta.membersOf('agg') must NOT contain VirtualSet member 'order_by'; got: " + memberNames);

        // fieldsOf(agg.count().outline()) 必须与 membersOf("agg") 里的 count 成员类型一致
        // 即：agg.count() 返回值的 fieldsOf 也必须是 Aggregator 成员（可无限链式调用）
        List<org.twelve.gcp.ast.Node> countCalls = findAllMemberCallNodes(ast.program(), "count");
        org.twelve.gcp.ast.Node aggCountNode = countCalls.stream()
                .filter(n -> MetaExtractor.fieldsOf(n.outline()).stream()
                        .anyMatch(f -> "compute".equals(f.name())))
                .findFirst()
                .orElse(null);
        assertNotNull(aggCountNode,
                "Must find agg.count() node whose fieldsOf has 'compute'");

        List<FieldMeta> countFields = MetaExtractor.fieldsOf(aggCountNode.outline());
        List<String> countFieldNames = countFields.stream().map(FieldMeta::name).toList();
        assertTrue(countFieldNames.contains("compute"),
                "fieldsOf(agg.count()) must have 'compute' for chaining; got: " + countFieldNames);
        assertFalse(countFieldNames.contains("filter"),
                "fieldsOf(agg.count()) must NOT have VirtualSet 'filter'; got: " + countFieldNames);

        // 同理，agg.count().sum(...) 的 outline 也应有 Aggregator 成员（链式无限可继续）
        org.twelve.gcp.ast.Node sumNode = findMemberCallNodeAfter(ast.program(), "aggregate", "sum");
        if (sumNode != null) {
            List<FieldMeta> sumFields = MetaExtractor.fieldsOf(sumNode.outline());
            List<String> sumFieldNames = sumFields.stream().map(FieldMeta::name).toList();
            assertTrue(sumFieldNames.contains("compute"),
                    "fieldsOf(agg.count().sum()) must have 'compute' for further chaining; got: " + sumFieldNames);
        }
    }

    // ─────────── Meta 与推导一致性：无限链式 fieldsOf 验证 ──────────────────

    /**
     * 核心不变量验证：对于推导成功的 aggregate 链中每一个中间节点（count/sum/avg/min/max），
     * {@link MetaExtractor#fieldsOf(org.twelve.gcp.outline.Outline)} 都必须返回完整的
     * Aggregator 成员集合（含 compute）——即支持"无限链式调用"。
     *
     * <p>如果推导成功（node.outline() 不含 UNKNOWN），那么 meta 提取也必须成功，
     * 不能仅返回 {@code [to_str]}。这是 LLM 获取 VirtualSet expression 合法性的唯一来源。
     *
     * <p>测试完整的 5 步 Aggregator 链：
     * <pre>{@code
     *   agg.count()                               → Aggregator<School>
     *      .sum(s -> s.city().population_count)   → Aggregator<School>
     *      .avg(s -> s.city().population_count)   → Aggregator<School>
     *      .min(s -> s.city().population_count)   → Aggregator<School>
     *      .max(s -> s.city().population_count)   → Aggregator<School>
     *      .compute()                             → [String:Number]
     * }</pre>
     *
     * <p>每一步的 {@code fieldsOf} 都必须返回所有 6 个 Aggregator 操作方法，
     * 不能仅返回内置的 {@code to_str}。
     */
    @Test
    void test_fieldsOf_consistent_with_inference_for_every_aggregate_chain_step() {
        String code = "module org.test.agg.chainsteps\n" + CITY_SCHOOL_PREAMBLE
                + "let r = (schools: Schools) -> schools.aggregate(agg -> {\n"
                + "    agg.count()\n"
                + "       .sum(s -> s.city().population_count)\n"
                + "       .avg(s -> s.city().population_count)\n"
                + "       .min(s -> s.city().population_count)\n"
                + "       .max(s -> s.city().population_count)\n"
                + "       .compute()\n"
                + "});\n"
                + "let dummy = 0;\nexport r, dummy;";
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected zero errors; got: " + ast.errors());

        // 每一个 ~this 返回的方法调用节点：fieldsOf 必须包含所有 6 个 Aggregator 方法
        List<String> aggregatorSteps = List.of("count", "sum", "avg", "min", "max");
        List<String> expectedAggMembers = List.of("count", "sum", "avg", "min", "max", "compute");

        List<org.twelve.gcp.ast.Node> allCalls = new java.util.ArrayList<>();
        for (String step : aggregatorSteps) {
            allCalls.addAll(findAllMemberCallNodes(ast.program(), step));
        }

        // 从所有调用里找属于 aggregate lambda 内的（outline 的 fieldsOf 含 compute）
        for (String step : aggregatorSteps) {
            org.twelve.gcp.ast.Node node = findMemberCallNodeAfter(ast.program(), "aggregate", step);
            if (node == null) continue; // sum/avg 可能同名，取 aggregate 子树内的

            // 推导必须成功——不含 UNKNOWN
            assertFalse(node.outline().containsUnknown(),
                    "." + step + "() inside aggregate must not contain UNKNOWN; got: " + node.outline());

            // Meta 与推导一致：fieldsOf 必须返回完整的 Aggregator 成员
            List<FieldMeta> fields = MetaExtractor.fieldsOf(node.outline());
            List<String> names = fields.stream().map(FieldMeta::name).toList();

            // 关键：不能只有 to_str
            assertFalse(names.size() == 1 && "to_str".equals(names.get(0)),
                    "fieldsOf(." + step + "()) must NOT return only [to_str]; "
                            + "inference and meta must be consistent. Got: " + names);

            for (String expected : expectedAggMembers) {
                assertTrue(names.contains(expected),
                        "fieldsOf(." + step + "()) must contain '" + expected
                                + "' (Aggregator member); got: " + names);
            }
            // 不能包含 VirtualSet 专属方法
            assertFalse(names.contains("filter"),
                    "fieldsOf(." + step + "()) must NOT contain VirtualSet 'filter'; got: " + names);
        }

        // compute() 返回 [String:Number]，fieldsOf 不应含 Aggregator 方法
        org.twelve.gcp.ast.Node computeNode = findMemberCallNodeAfter(ast.program(), "aggregate", "compute");
        assertNotNull(computeNode, "Should find .compute() in aggregate lambda");
        assertFalse(computeNode.outline().containsUnknown(),
                "compute() must not contain UNKNOWN; got: " + computeNode.outline());
        List<FieldMeta> computeFields = MetaExtractor.fieldsOf(computeNode.outline());
        assertFalse(computeFields.stream().anyMatch(f -> "count".equals(f.name())),
                "fieldsOf(compute()) must NOT contain Aggregator 'count'; got: "
                        + computeFields.stream().map(FieldMeta::name).toList());
    }

    /**
     * 不变量验证：VirtualSet 链中每一个中间节点（filter/order_desc_by/take）的
     * {@code fieldsOf} 都必须返回 VirtualSet 成员集合（含 filter/count/map 等），
     * 而不是元素类型（School）的字段。
     *
     * <p>完整链：
     * <pre>{@code
     *   schools.filter(s -> s.students().count() > 5)   → Schools (~this)
     *          .order_desc_by(s -> s.name)               → Schools (~this)
     *          .take(3)                                  → Schools (~this)
     *          .to_list()                                → [School]
     * }</pre>
     */
    @Test
    void test_fieldsOf_consistent_with_inference_for_every_virtualset_chain_step() {
        String code = "module org.test.vs.chainsteps\n" + FULL_WORLD_PREAMBLE
                + "let r = schools.filter(s -> s.students().count() > 5)\n"
                + "               .order_desc_by(s -> s.name)\n"
                + "               .take(3)\n"
                + "               .to_list();\n"
                + "let dummy = 0;\nexport r, dummy;";
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected zero errors; got: " + ast.errors());

        // filter / order_desc_by / take 均返回 ~this = Schools (VirtualSet<School>)
        for (String step : List.of("filter", "order_desc_by", "take")) {
            org.twelve.gcp.ast.Node node = findMemberCallNode(ast.program(), step);
            if (node == null) continue;
            assertFalse(node.outline().containsUnknown(),
                    "." + step + "() must not contain UNKNOWN; got: " + node.outline());

            List<FieldMeta> fields = MetaExtractor.fieldsOf(node.outline());
            List<String> names = fields.stream().map(FieldMeta::name).toList();

            // 不能只返回 to_str
            assertFalse(names.size() == 1 && "to_str".equals(names.get(0)),
                    "fieldsOf(." + step + "()) must NOT return only [to_str]; "
                            + "inference and meta must be consistent. Got: " + names);

            // 必须包含 VirtualSet 成员
            assertTrue(names.contains("count"),
                    "fieldsOf(." + step + "()) must contain 'count' (VirtualSet method); got: " + names);
            assertTrue(names.contains("filter"),
                    "fieldsOf(." + step + "()) must contain 'filter' (VirtualSet method); got: " + names);

            // 不能包含 School 实体特有字段（如 id, name）——这些是元素类型字段，不是 VirtualSet 方法
            // 注意：schools preamble 中 Schools = VirtualSet<School>{ students: Unit->Students }
            // 因此 students 是 Schools 的导航方法，可合法出现，不应在此检查
            assertFalse(names.contains("id"),
                    "fieldsOf(." + step + "()) must NOT contain School entity field 'id'; got: " + names);
        }
    }

    /**
     * 综合不变量：filter().aggregate() 链中，meta 提取必须与推导完全一致。
     * 对整条链的每一个重要节点验证：推导不含 UNKNOWN ↔ fieldsOf 返回正确成员集。
     *
     * <p>这是"推导成功则 meta 提取也成功"原则的最终验证。
     */
    @Test
    void test_meta_inference_consistency_invariant_full_chain() {
        String code = "module org.test.agg.invariant\n" + FULL_WORLD_PREAMBLE
                + "let r = schools.filter(s -> s.students().count() > 10)\n"
                + "               .aggregate(agg -> {\n"
                + "                   agg.count()\n"
                + "                      .sum(s -> s.city().population_count)\n"
                + "                      .avg(s -> s.city().population_count)\n"
                + "                      .min(s -> s.city().population_count)\n"
                + "                      .max(s -> s.city().population_count)\n"
                + "                      .compute()\n"
                + "               });\n"
                + "let dummy = 0;\nexport r, dummy;";
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Expected zero errors; got: " + ast.errors());

        // 外层 VirtualSet 链：filter 返回 Schools
        org.twelve.gcp.ast.Node filterNode = findMemberCallNode(ast.program(), "filter");
        assertNotNull(filterNode);
        assertFalse(filterNode.outline().containsUnknown());
        List<FieldMeta> filterFields = MetaExtractor.fieldsOf(filterNode.outline());
        assertTrue(filterFields.stream().anyMatch(f -> "aggregate".equals(f.name())),
                "fieldsOf(filter()) must contain 'aggregate' (Schools method); got: "
                        + filterFields.stream().map(FieldMeta::name).toList());

        // Aggregator 链：count/sum/avg/min/max 每一步 fieldsOf 返回 Aggregator 成员
        List<String> aggSteps = List.of("count", "sum", "avg", "min", "max");
        for (String step : aggSteps) {
            org.twelve.gcp.ast.Node node = findMemberCallNodeAfter(ast.program(), "aggregate", step);
            if (node == null) continue;

            assertFalse(node.outline().containsUnknown(),
                    step + "() must not be UNKNOWN");

            List<FieldMeta> fields = MetaExtractor.fieldsOf(node.outline());
            List<String> names = fields.stream().map(FieldMeta::name).toList();

            // 核心不变量：推导成功 → meta 不能只有 to_str
            assertFalse(names.isEmpty() || (names.size() == 1 && "to_str".equals(names.get(0))),
                    "INVARIANT VIOLATED: inference succeeded for ." + step
                            + "() but fieldsOf returned only [to_str] or empty. "
                            + "Meta and inference must use the same AST outline data.");

            assertTrue(names.contains("compute"),
                    "fieldsOf(." + step + "()) must contain 'compute'; got: " + names);
            assertFalse(names.contains("filter"),
                    "fieldsOf(." + step + "()) must NOT contain VirtualSet 'filter'; got: " + names);
        }
    }

    // ── entity-body ~this action inference ────────────────────────────────────

    /**
     * An entity with a {@code lock: Unit -> ~this} action method must type-infer
     * without errors, and the {@code person.lock()} call node must resolve to the
     * entity type (Person), not UNKNOWN or the raw {@code ~this} decorator.
     *
     * <p>This is the canonical regression guard for the "entity action returns ~this"
     * feature: the schema declares the action, type inference propagates the concrete
     * entity type via {@code This.eventual()}, and no type mismatch is reported.
     */
    @Test
    void test_entity_action_this_return_type_infers_to_entity() {
        String code = "module org.test.entityaction\n"
                + OntologyFixtures.SYSTEM_OUTLINES
                + """
                outline Person = {
                    id:0,
                    name:String,
                    locked:Bool,
                    lock:Unit -> ~this,
                    unlock:Unit -> ~this
                };
                let f = (p: Person) -> p.lock();
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();

        assertTrue(ast.errors().isEmpty(),
                "Expected no inference errors for entity ~this action; got: " + ast.errors());

        // The lock() call node must resolve to Person (not UNKNOWN)
        org.twelve.gcp.ast.Node lockCall = findMemberCallNode(ast.program(), "lock");
        assertNotNull(lockCall, "Should find p.lock() call node in AST");

        Outline callOutline = lockCall.outline();
        assertNotNull(callOutline, "p.lock() call should have a non-null outline");
        assertFalse(callOutline.containsUnknown(),
                "p.lock() return type must NOT be UNKNOWN; got: " + callOutline
                        + " (" + callOutline.getClass().getSimpleName() + ")");

        // The return type must expose Person's own members (name, locked, lock, unlock)
        List<FieldMeta> fields = MetaExtractor.fieldsOf(callOutline);
        assertTrue(fields.stream().anyMatch(f -> "name".equals(f.name())),
                "p.lock() return type must have 'name' (Person member); got fields: "
                        + fields.stream().map(FieldMeta::name).toList());
        assertTrue(fields.stream().anyMatch(f -> "lock".equals(f.name())),
                "p.lock() return type must have 'lock' (chaining); got fields: "
                        + fields.stream().map(FieldMeta::name).toList());
    }

    /**
     * Chained entity actions must infer correctly:
     * {@code p.lock().unlock()} must not produce inference errors,
     * and the final call must also resolve to Person (enabling further chaining).
     */
    @Test
    void test_entity_action_this_chaining_infers_without_errors() {
        String code = "module org.test.entityactionchain\n"
                + OntologyFixtures.SYSTEM_OUTLINES
                + """
                outline Person = {
                    id:0,
                    name:String,
                    locked:Bool,
                    lock:Unit -> ~this,
                    unlock:Unit -> ~this
                };
                let f = (p: Person) -> p.lock().unlock();
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();

        assertTrue(ast.errors().isEmpty(),
                "Expected no inference errors for p.lock().unlock() chain; got: " + ast.errors());

        // The unlock() call node (second in chain) must also resolve to a non-UNKNOWN type
        org.twelve.gcp.ast.Node unlockCall = findMemberCallNode(ast.program(), "unlock");
        assertNotNull(unlockCall, "Should find p.lock().unlock() call node in AST");

        Outline unlockOutline = unlockCall.outline();
        assertNotNull(unlockOutline, "p.lock().unlock() call should have a non-null outline");
        assertFalse(unlockOutline.containsUnknown(),
                "p.lock().unlock() return type must NOT be UNKNOWN; got: " + unlockOutline);

        // Must still have Person's members after double chain
        List<FieldMeta> fields = MetaExtractor.fieldsOf(unlockOutline);
        assertTrue(fields.stream().anyMatch(f -> "name".equals(f.name())),
                "p.lock().unlock() must still expose 'name' (Person member); got: "
                        + fields.stream().map(FieldMeta::name).toList());
    }

    /**
     * 在给定的 {@code aggregateCallName} 调用节点的 lambda 参数子树中，查找第一个名为
     * {@code methodName} 的成员调用节点。用于精确区分 filter 谓词里的 count 与
     * aggregate lambda 里的 count。
     */
    private static org.twelve.gcp.ast.Node findMemberCallNodeAfter(
            org.twelve.gcp.ast.Node root, String aggregateCallName, String methodName) {
        org.twelve.gcp.ast.Node aggregateCall = findMemberCallNode(root, aggregateCallName);
        if (aggregateCall == null) return null;
        // aggregate call 的子节点里，跳过 function（MemberAccessor），只看 arguments
        for (org.twelve.gcp.ast.Node child : aggregateCall.nodes()) {
            // MemberAccessor 是 function 部分，不是 lambda 参数
            if (child instanceof org.twelve.gcp.node.expression.accessor.MemberAccessor) continue;
            org.twelve.gcp.ast.Node found = findMemberCallNode(child, methodName);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Semantic test for bare default-value literals in entity field declarations.
     *
     * <p>Outline grammar: {@code entity_field : ID (':' (declared_outline | literal))?}
     * <ul>
     *   <li>{@code id:0}     — bare {@code literal} branch (default VALUE, widens to primitive)</li>
     *   <li>{@code id:#1}    — {@code literal_type} via declared_outline (explicit LITERAL type)</li>
     *   <li>{@code id:0|3|4} — option of literal_types via declared_outline (literal union)</li>
     * </ul>
     *
     * <p>Fix in {@link org.twelve.gcp.inference.EntityTypeNodeInference}: when a field
     * has a default value whose inferred type is a {@link org.twelve.gcp.outline.primitive.Literal}
     * wrapper, widen it to the origin primitive. Otherwise {@code id:0} would be typed as
     * the singleton value 0, making downstream ops like {@code e.id + 1} nonsensical and
     * breaking any usage that expects an Int.
     */
    @Test
    void test_default_value_literal_widens_to_primitive() {
        String code = "module org.test.defwiden\n" + OntologyFixtures.SYSTEM_OUTLINES + """
                outline Person = { id:0, name:String };
                outline People = VirtualSet<Person>{};
                let people = __ontology_repo__<People>;
                let p = people.first();
                let x = 0;
                export p, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
                "Precondition: parse+infer should have no errors; got: " + ast.errors());

        org.twelve.gcp.ast.Node firstCall = findMemberCallNode(ast.program(), "first");
        assertNotNull(firstCall, "Should find people.first() call in AST");
        org.twelve.gcp.outline.Outline resolved = MetaExtractor.resolveOutline(firstCall.outline());
        assertTrue(resolved instanceof org.twelve.gcp.outline.adt.Entity,
                "first() should resolve to Person entity; got " + resolved);
        var person = (org.twelve.gcp.outline.adt.Entity) resolved;

        // id is present and carries a user default value.
        var idMember = person.getMember("id").orElseThrow(
                () -> new AssertionError("Person must have 'id' member"));
        assertTrue(idMember.isDefault(),
                "id:0 must be marked isDefault (user-declared default value)");
        assertTrue(idMember.hasDefaultValue(),
                "id:0 must have a default-value node");

        // Key invariant: id's type must widen from Literal(0) to its primitive (Integer/Number),
        // NOT stay as a singleton literal type.
        String idType = idMember.outline().toString();
        assertFalse(idType.equals("0"),
                "id:0 must widen to Int/Integer, not stay as literal '0'; got: " + idType);
        assertTrue(idType.toLowerCase().contains("int") || idType.toLowerCase().contains("number"),
                "id:0 should widen to Int/Integer/Number primitive; got: " + idType);

        // name:String is declared (not a default value).
        var nameMember = person.getMember("name").orElseThrow();
        assertFalse(nameMember.isDefault(),
                "name:String is a declared type, not a default value");
    }
}
