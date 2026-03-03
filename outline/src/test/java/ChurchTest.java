import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.OutlineParser;

public class ChurchTest {
    @Test
    void test_church_types() {
        ASF asf = new ASF();
        AST ast = new OutlineParser().parse(asf, """
let zero  = f -> x -> x;
let succ  = n -> f -> x -> f(n(f)(x));
let one   = succ(zero);
let two   = succ(one);
let five  = succ(succ(succ(succ(succ(zero)))));
let church_mul = m -> n -> f -> m(n(f));
let ten   = church_mul(two)(five);
let decode = n -> n(x -> x + 1)(0);
decode(ten)
""");
        asf.infer();
        var stmts = ast.program().body().statements();
        String[] names = {"zero","succ","one","two","five","church_mul","ten","decode","decode(ten)"};
        for (int i = 0; i < names.length; i++) {
            System.out.printf("  %-14s → %s%n", names[i], stmts.get(i).get(0).outline());
        }
        System.out.println();
        System.out.println("=== ERRORS (" + ast.errors().size() + ") ===");
        for (var e : ast.errors()) {
            var n = e.node();
            System.out.println("  [" + e.errorCode() + "] '" + n.lexeme().substring(0, Math.min(50, n.lexeme().length())) + "' → " + n.outline());
        }
    }
}
