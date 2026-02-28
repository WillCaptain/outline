import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.GCPConverter;
import org.twelve.outline.OutlineParser;

public class ErrorDebugTest {
    private static final OutlineParser parser = new OutlineParser(new GCPConverter());

    @Test
    void print_inherited_this_errors() {
        AST ast = ASTHelper.mockInheritedThis();
        ast.asf().infer();
        System.out.println("=== ERRORS (" + ast.errors().size() + ") ===");
        for (var e : ast.errors()) {
            System.out.println("  code=" + e.errorCode() + 
                " | msg=" + e.message() + 
                " | node=" + e.node().getClass().getSimpleName() +
                " | lexeme='" + e.node().lexeme().substring(0, Math.min(20, e.node().lexeme().length())) + "'");
        }
    }
}
