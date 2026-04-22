import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.exception.GCPErrCode;
import org.twelve.outline.OutlineParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural tag / row-polymorphic ADT tests.
 *
 * <p>Design principles verified here:
 * <ul>
 *   <li>Free-standing {@code Tag{...}} constructs a structural entity; the tag is part of the
 *       type's identity but the structure is defined by the literal's fields. Two literals sharing
 *       a tag but with different structures are two different types — both legal.</li>
 *   <li>{@code Owner.Variant{...}} is an explicit structural upcast to {@code Owner}. The literal
 *       must structurally satisfy the variant under row-polymorphism (all required fields present
 *       and compatible; extras allowed).</li>
 *   <li>Error messages emphasise <em>belonging failure</em> to the ADT, not "field not found" on
 *       the owner type.</li>
 * </ul>
 */
class StructuralVariantTest {

    private static AST parse(String code) {
        AST ast = new OutlineParser().parse(code);
        ast.asf().infer();
        return ast;
    }

    private static boolean hasError(AST ast, GCPErrCode code) {
        return ast.errors().stream().anyMatch(e -> e.errorCode() == code);
    }

    // ── Free-standing structural tags ─────────────────────────────────────────

    @Test
    void free_standing_tag_with_any_structure_is_legal() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let a = Pending{name:"Will"};
            let b = Pending{boss:"will"};
            let c = Pending{name:"Will", extra:42};
            """);
        assertTrue(ast.errors().isEmpty(), "free structural tags are all legal, errors=" + ast.errors());
    }

    // ── Upcast (ascription) to ADT via structural is ──────────────────────────

    @Test
    void ascription_upcast_with_row_polymorphism() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let s:Status = Pending{name:"Will"};
            let t:Status = Pending{name:"Will", extra:42};
            """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }

    @Test
    void ascription_upcast_with_wrong_structure_errors() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let s:Status = Pending{boss:"will"};
            """);
        assertTrue(hasError(ast, GCPErrCode.OUTLINE_MISMATCH),
                "should report mismatch, errors=" + ast.errors());
    }

    // ── Qualified constructor  Owner.Variant{...} ────────────────────────────

    @Test
    void qualified_ctor_happy_path() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let a = Status.Approved{boss:"will"};
            """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }

    @Test
    void qualified_ctor_row_polymorphism_allows_extras() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let a = Status.Approved{boss:"will", extra:42};
            """);
        assertTrue(ast.errors().isEmpty(), "extras should be allowed (row poly), errors=" + ast.errors());
    }

    @Test
    void qualified_ctor_wrong_structure_reports_belonging_failure() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let e = Status.Pending{age:10};
            """);
        assertTrue(hasError(ast, GCPErrCode.OUTLINE_MISMATCH),
                "should report mismatch, errors=" + ast.errors());
        boolean mentionsBelonging = ast.errors().stream()
                .anyMatch(er -> er.toString().contains("does not belong to 'Pending'")
                        && er.toString().contains("missing 'name'"));
        assertTrue(mentionsBelonging,
                "diagnostic should emphasise belonging failure with missing field, errors="
                        + ast.errors());
    }

    @Test
    void qualified_ctor_unknown_variant_reports_not_a_variant() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let e = Status.Unknown{x:1};
            """);
        assertTrue(hasError(ast, GCPErrCode.OUTLINE_MISMATCH),
                "errors=" + ast.errors());
        boolean mentionsNotAVariant = ast.errors().stream()
                .anyMatch(er -> er.toString().contains("'Unknown'")
                        && er.toString().contains("not a variant of"));
        assertTrue(mentionsNotAVariant,
                "diagnostic should mention 'not a variant of', errors=" + ast.errors());
    }

    @Test
    void qualified_ctor_wrong_field_type_reports_type_mismatch() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let e = Status.Pending{name:42};
            """);
        assertTrue(hasError(ast, GCPErrCode.OUTLINE_MISMATCH), "errors=" + ast.errors());
        boolean mentionsFieldMismatch = ast.errors().stream()
                .anyMatch(er -> er.toString().contains("type mismatch on")
                        && er.toString().contains("'name'"));
        assertTrue(mentionsFieldMismatch,
                "diagnostic should pinpoint the mistyped field, errors=" + ast.errors());
    }

    // ── Match with structural tag + field-alias ──────────────────────────────

    // ── Call-site argument upcast (regression for empty-Option extend-constraint bug) ──

    @Test
    void call_site_accepts_free_tag_when_formal_is_adt() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let f = x:Status -> "ok";
            let r = f(Pending{name:"Will"});
            """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }

    @Test
    void call_site_accepts_qualified_ctor_when_formal_is_adt() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let f = x:Status -> "ok";
            let r = f(Status.Approved{boss:"will"});
            """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }

    @Test
    void call_site_rejects_wrong_structure() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let f = x:Status -> "ok";
            let r = f(Pending{boss:"will"});
            """);
        assertFalse(ast.errors().isEmpty(),
                "wrong-structure call should error, errors=" + ast.errors());
    }

    @Test
    void match_structural_tag_with_field_alias_inside_lambda() {
        AST ast = parse("""
            outline Status = Pending{name:String}|Approved{boss:String};
            let f = x:Status -> {
              match x{
                Pending{name as n}-> n,
                Approved{boss as n}->n,
                _->"others"
              }
            };
            """);
        assertTrue(ast.errors().isEmpty(), "errors=" + ast.errors());
    }
}
