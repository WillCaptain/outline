import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.meta.*;
import org.twelve.gcp.outlineenv.AstScope;
import org.twelve.gcp.outlineenv.EnvSymbol;
import org.twelve.gcp.outlineenv.SYMBOL_CATEGORY;
import org.twelve.outline.OutlineParser;

public class DiagMeta {
    private final OutlineParser parser = new OutlineParser();
    private static final String VS_PREAMBLE = """
            outline VirtualSet = <a>{
                filter: (a -> Bool) -> ~this,
                first:  Unit -> a,
                count:  Unit -> Int
            };
            """;

    @Test
    void diag_lookup() {
        String code = "module org.test.diag2\n" + VS_PREAMBLE + """
                outline Country = { code: String, name: String };
                outline Countries = VirtualSet<Country>{};
                let countries = __ontology_repo__<Countries>;
                let first_country = countries.first();
                export countries, first_country;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        ModuleMeta meta = ast.meta();

        System.out.println("=== Countries sym id in symbols ===");
        for (AstScope scope : ast.symbolEnv().allScopes()) {
            for (var entry : scope.symbols().entrySet()) {
                String name = entry.getKey();
                if (name.equals("Countries") || name.equals("countries") || name.equals("first_country") || name.equals("Country")) {
                    EnvSymbol sym = entry.getValue();
                    System.out.println("  sym=[" + name + "] cat=" + sym.category()
                        + " id=" + sym.outline().id() + " toString=" + sym.outline().toString().substring(0, Math.min(60, sym.outline().toString().length())));
                }
            }
        }
        
        System.out.println("=== meta.resolve ===");
        int pos = code.indexOf("export");
        var countriesSym = meta.resolve("countries", pos);
        System.out.println("  countries.type = " + (countriesSym != null ? countriesSym.type() : "null"));
        var firstSym = meta.resolve("first_country", pos);
        System.out.println("  first_country.type = " + (firstSym != null ? firstSym.type() : "null"));
    }
}
