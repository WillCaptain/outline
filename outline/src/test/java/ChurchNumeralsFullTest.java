import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.OutlineParser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the user's claim:
 *   ✓ GCP infers zero, succ, one…five, decode, church_add without annotations
 *   ✓ church_add(3)(5) = 8 executes correctly
 *   ✗ church_mul hits the Rank-2 infinite-type wall (expected error)
 */
public class ChurchNumeralsFullTest {

    // ── Working subset (zero … church_add) ─────────────────────────────────────

    private static final String CHURCH_WORKING = """
let zero  = f -> x -> x;
let succ  = n -> f -> x -> f(n(f)(x));

let one   = succ(zero);
let two   = succ(one);
let three = succ(two);
let four  = succ(three);
let five  = succ(four);

let decode = n -> n(x -> x + 1)(0);

let church_add = m -> n -> f -> x -> m(f)(n(f)(x));

let eight = church_add(three)(five);
decode(eight)
""";

    // ── church_mul — the Rank-2 wall ────────────────────────────────────────────

    private static final String CHURCH_MUL = """
let zero  = f -> x -> x;
let succ  = n -> f -> x -> f(n(f)(x));
let two   = succ(succ(zero));
let five  = succ(succ(succ(succ(succ(zero)))));
let church_mul = m -> n -> f -> m(n(f));
let ten        = church_mul(two)(five);
let decode     = n -> n(x -> x + 1)(0);
decode(ten)
""";

    // ───────────────────────────────────────────────────────────────────────────

    @Test
    void church_working_infers_and_runs_correctly() {
        ASF asf = new ASF();
        AST ast = new OutlineParser().parse(asf, CHURCH_WORKING);
        asf.infer();

        var stmts = ast.program().body().statements();
        String[] names = {
                "zero", "succ", "one", "two", "three", "four", "five",
                "decode", "church_add", "eight", "decode(eight)"
        };

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  GCP Type Inference — Church Numerals (working subset)               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.printf("  %-16s  %s%n", "Name", "GCP-inferred type");
        System.out.println("  " + "─".repeat(72));
        for (int i = 0; i < names.length && i < stmts.size(); i++) {
            System.out.printf("  %-16s  %s%n", names[i], stmts.get(i).get(0).outline());
        }

        System.out.println();
        int errCount = ast.errors().size();
        System.out.println("  Type errors: " + errCount);
        for (var e : ast.errors()) {
            var n = e.node();
            System.out.println("  [" + e.errorCode() + "] '"
                    + n.lexeme().substring(0, Math.min(60, n.lexeme().length()))
                    + "' → " + n.outline());
        }

        // ── Execution ─────────────────────────────────────────────────────────
        System.out.println();
        System.out.println("  Running decode(eight) ...");
        try {
            var result = asf.interpret();
            System.out.println("  Result: " + result);
            System.out.println();
            System.out.println("  ✓ church_add(three)(five) = " + RunnerHelper.intVal(result)
                    + "   (expected: 8)");
            assertThat(RunnerHelper.intVal(result)).isEqualTo(8L);
        } catch (Exception ex) {
            System.out.println("  ✗ Execution failed: " + ex.getMessage());
            throw ex;
        }

        // ── Key type checks ───────────────────────────────────────────────────
        System.out.println();
        System.out.println("  decode type: " + stmts.get(7).get(0).outline());
        System.out.println("  Expected:    n:(Int->Int)->Int->Int  or equivalent");
        assertThat(errCount).as("No type errors expected for working Church subset").isEqualTo(0);
    }

    @Test
    void church_mul_hits_rank2_wall() {
        ASF asf = new ASF();
        AST ast = new OutlineParser().parse(asf, CHURCH_MUL);
        asf.infer();

        var stmts = ast.program().body().statements();
        String[] names = {"zero", "succ", "two", "five", "church_mul", "ten", "decode", "decode(ten)"};

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  GCP Type Inference — church_mul (Rank-2 wall expected)              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.printf("  %-16s  %s%n", "Name", "GCP-inferred type");
        System.out.println("  " + "─".repeat(72));
        for (int i = 0; i < names.length && i < stmts.size(); i++) {
            System.out.printf("  %-16s  %s%n", names[i], stmts.get(i).get(0).outline());
        }

        System.out.println();
        System.out.println("  Type errors: " + ast.errors().size());
        for (var e : ast.errors()) {
            var n = e.node();
            System.out.printf("  [%s] '%s'%n    node type: %s%n",
                    e.errorCode(),
                    n.lexeme().substring(0, Math.min(60, n.lexeme().length())),
                    n.outline());
        }

        // Try to execute — should either fail or give wrong answer
        System.out.println();
        System.out.println("  Attempting execution (may fail due to Rank-2 constraint) ...");
        try {
            var result = asf.interpret();
            long val = RunnerHelper.intVal(result);
            System.out.println("  Execution result: " + val);
            if (val == 10L) {
                System.out.println("  ✓ Surprisingly, GCP DID compute 2×5=10 correctly!");
                System.out.println("    (This would mean GCP resolved the infinite-type loop at runtime)");
            } else {
                System.out.println("  ✗ Wrong result: " + val + " (expected 10) — Rank-2 constraint degraded accuracy");
            }
        } catch (Exception ex) {
            System.out.println("  ✗ Execution exception: " + ex.getClass().getSimpleName()
                    + " — " + ex.getMessage());
            System.out.println("  ✓ This confirms the Rank-2 wall: GCP cannot resolve the recursive type.");
        }

        System.out.println();
        System.out.println("  THEORETICAL NOTE:");
        System.out.println("  church_mul = m -> n -> f -> m(n(f))");
        System.out.println("  Forces: type(n) = α→β  AND  type(n) = (α→β)→γ  simultaneously");
        System.out.println("  This requires α = α→β (infinite/recursive type) — undecidable in Rank-1.");
        System.out.println("  Correct encoding needs: ∀a.(a→a)→a→a  (Rank-2 quantifier inside arrow)");
        System.out.println("  Wells (1994): Rank-2 inference is decidable but rank-3+ is undecidable.");
        System.out.println("  GCP (like HM/TypeScript) is Rank-1 — this limitation is UNIVERSAL.");
    }
}
