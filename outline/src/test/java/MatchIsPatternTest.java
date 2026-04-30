import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the type-pattern sugar in match arms:
 * <pre>
 *   match x {
 *       is String  -> "string",
 *       is Nothing -> "nothing"
 *   }
 * </pre>
 * is desugared to the equivalent guard form
 * {@code x if(x is String) -> "string", x if(x is Nothing) -> "nothing"} so
 * arm bodies see {@code x} narrowed to the type by the existing GCP machinery.
 */
public class MatchIsPatternTest {

    @BeforeAll
    static void warmUp() {
        ASTHelper.parser.toString();
    }

    private static AST parse(String code) {
        return new OutlineParser().parse(new ASF(), code);
    }

    @Test
    void is_pattern_on_nullable_param_infers_without_errors() {
        // The is-pattern is pure sugar over the existing guard form. The guard form
        // already emits a soft "non-exhaustive match" warning (see MatchExhaustTest)
        // because guards are not consumed by the exhaustiveness analyser. The sugar
        // must produce the same diagnostic profile, no more and no less.
        AST sugar = parse("""
                let f = x:String?->{
                    match x {
                        is String  -> "string",
                        is Nothing -> "nothing"
                    }
                };
                """);
        AST guarded = parse("""
                let f = x:String?->{
                    match x {
                        x if(x is String)  -> "string",
                        x if(x is Nothing) -> "nothing"
                    }
                };
                """);
        assertTrue(sugar.asf().infer());
        assertTrue(guarded.asf().infer());
        assertEquals(guarded.errors().size(), sugar.errors().size(),
                "sugar should mirror guarded-form diagnostics; sugar: " + sugar.errors()
                        + " | guarded: " + guarded.errors());
    }

    @Test
    void is_pattern_is_equivalent_to_guarded_form() {
        AST sugar = parse("""
                let f = x:String?->{
                    match x {
                        is String  -> 1,
                        is Nothing -> 0
                    }
                };
                """);
        AST guarded = parse("""
                let f = x:String?->{
                    match x {
                        x if(x is String)  -> 1,
                        x if(x is Nothing) -> 0
                    }
                };
                """);
        assertTrue(sugar.asf().infer());
        assertTrue(guarded.asf().infer());
        assertEquals(guarded.errors().size(), sugar.errors().size(),
                "is-pattern sugar should produce the same diagnostics as the guarded form");
    }

    @Test
    void is_pattern_call_on_nullable_argument_does_not_add_extra_errors() {
        AST sugar = parse("""
                var a:String? = null;
                let f = x:String?->{
                    match x {
                        is String  -> "string",
                        is Nothing -> "nothing"
                    }
                };
                f(a)
                """);
        AST guarded = parse("""
                var a:String? = null;
                let f = x:String?->{
                    match x {
                        x if(x is String)  -> "string",
                        x if(x is Nothing) -> "nothing"
                    }
                };
                f(a)
                """);
        assertTrue(sugar.asf().infer());
        assertTrue(guarded.asf().infer());
        assertEquals(guarded.errors().size(), sugar.errors().size(),
                "sugar should not add new errors when called; sugar: " + sugar.errors()
                        + " | guarded: " + guarded.errors());
    }
}
