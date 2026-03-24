import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.exception.GCPErrCode;
import org.twelve.gcp.exception.GCPError;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MatchExhaustTest {
    @Test
    void test_match_without_wildcard() {
        ASF asf = new ASF();
        AST ast = new OutlineParser().parse(asf, """
outline Weekday = Mon | Tue | Wed | Thu | Fri | Sat | Sun;

let is_weekend = d -> match d {
    Sat -> true,
    Sun -> true
};

let r1 = is_weekend(Sat);
let r2 = is_weekend(Mon);
""");
        asf.infer();
        assertTrue(ast.errors().stream().anyMatch(e ->
                        e.errorCode() == GCPErrCode.NON_EXHAUSTIVE_MATCH &&
                        e.severity() == GCPError.Severity.WARNING &&
                        e.message().contains("widens to Option")),
                "non-exhaustive match should surface a warning diagnostic with a stable error code");
        print(ast, "without wildcard");
    }

    @Test
    void test_match_with_wildcard() {
        ASF asf = new ASF();
        AST ast = new OutlineParser().parse(asf, """
outline Weekday = Mon | Tue | Wed | Thu | Fri | Sat | Sun;

let is_weekend_2 = d -> match d {
    Sat -> true,
    Sun -> true,
    _   -> false
};

let r1 = is_weekend_2(Sat);
let r2 = is_weekend_2(Mon);
let r3 = is_weekend_2(42);
""");
        asf.infer();
        assertFalse(ast.errors().stream().anyMatch(e ->
                        e.errorCode() == GCPErrCode.NON_EXHAUSTIVE_MATCH),
                "exhaustive match should not emit a non-exhaustive diagnostic");
        print(ast, "with wildcard");
    }

    private void print(AST ast, String label) {
        System.out.println("\n=== " + label + " | ERRORS(" + ast.errors().size() + ") ===");
        for (var e : ast.errors()) {
            var n = e.node();
            System.out.println("  [" + e.errorCode() + "] '"
                + n.lexeme().substring(0, Math.min(40, n.lexeme().length()))
                + "' → " + n.outline());
        }
        System.out.println("  Statements:");
        for (var s : ast.program().body().statements()) {
            System.out.printf("    %-36s → %s%n",
                s.lexeme().substring(0, Math.min(36, s.lexeme().length())), s.outline());
        }
    }
}
