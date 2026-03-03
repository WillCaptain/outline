import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.OutlineParser;

public class IfBranchTest {
    @Test
    void test_if_with_console() {
        ASF asf = new ASF();
        AST ast = new OutlineParser().parse(asf, """
let result = x -> if (x > 0) x else "negative";
Console.log(result(5));
""");
        asf.infer();
        System.out.println("=== WITH-CONSOLE ERRORS (" + ast.errors().size() + ") ===");
        for (var e : ast.errors()) {
            var n = e.node();
            System.out.println("  code=" + e.errorCode()
                + " | msg=" + e.message().replace("\n"," ").substring(0, Math.min(80, e.message().replace("\n"," ").length()))
                + " | node=" + n.getClass().getSimpleName()
                + " | outline=" + n.outline());
        }
    }
}
