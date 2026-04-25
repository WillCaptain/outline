import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.exception.GCPErrCode;
import org.twelve.gcp.interpreter.value.Value;
import org.twelve.outline.OutlineParser;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for two grammar relaxations:
 *   1. Single-element tuples written as `(T,)` (Python/Rust-style trailing comma).
 *      Bare `(T)` remains grouping, NOT a tuple.
 *   2. Single-constructor ADTs like `outline Gender = Male(String, Int);` (without
 *      a `| Female...` alternative).
 */
public class TupleAndSingleCtorTest {

    private static final OutlineParser parser = ASTHelper.parser;

    private static AST parse(String code) {
        return parser.parse(new ASF(), code);
    }

    // ─────────────────────────── Problem 1: tuple shape ───────────────────────

    @Test
    void single_element_tuple_with_trailing_comma_parses() {
        AST ast = parse("let t: (String,) = (\"hi\",);");
        assertTrue(ast.errors().isEmpty(),
                "(T,) should parse as single-element tuple; got: " + ast.errors());
        assertTrue(ast.asf().infer(),
                "inference should succeed for single-element tuple");
    }

    @Test
    void capital_this_type_alias_parses_like_legacy_tilde_this() {
        String code = """
                outline Box = {
                    value: Int,
                    keep: Unit -> This,
                    legacy: Unit -> ~this
                };
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(),
                "`This` should parse as a type-level alias of `~this`; got: " + ast.errors());
        assertTrue(ast.asf().infer(), "inference should succeed for both This and ~this");
    }

    @Test
    void multi_element_tuple_still_parses() {
        AST ast = parse("let t: (String, Int) = (\"hi\", 1);");
        assertTrue(ast.errors().isEmpty(),
                "multi-element tuple should still work; got: " + ast.errors());
        assertTrue(ast.asf().infer(), "inference should succeed");
    }

    @Test
    void single_element_paren_without_comma_is_grouping_not_tuple() {
        // `(String)` is grouping of the type, so the RHS `"hi"` (a plain String)
        // should be assignable to it. If it were misparsed as a 1-tuple, typing
        // would fail.
        AST ast = parse("let s: (String) = \"hi\";");
        assertTrue(ast.errors().isEmpty(),
                "(T) should be grouping (equivalent to T); got: " + ast.errors());
        assertTrue(ast.asf().infer(), "inference should succeed");
    }

    // ───────────────────── Problem 2: single-constructor ADT ──────────────────

    @Test
    void single_constructor_tuple_variant_parses() {
        String code = """
                outline Gender = Male(String, Int);
                let g = Male("bob", 30);
                g
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(),
                "single-constructor ADT `Male(String,Int)` should parse; got: " + ast.errors());
        assertTrue(ast.asf().infer(),
                "inference should succeed for single-constructor variant");
    }

    @Test
    void multi_branch_adt_still_parses() {
        // Regression: ensure original `|`-separated ADT still works unchanged.
        String code = """
                outline Gender = Male(String, Int) | Female(String, Int);
                let g = Male("bob", 30);
                g
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(),
                "multi-branch ADT should still work; got: " + ast.errors());
        assertTrue(ast.asf().infer(), "inference should succeed");
    }

    @Test
    void single_constructor_with_single_element_tuple_type() {
        // Combines both fixes: declaration uses the trailing-comma tuple type;
        // the call site uses ordinary function-call syntax `Box(42)`.
        String code = """
                outline Wrap = Box(Int,);
                let w = Box(42);
                w
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(),
                "single-ctor with (T,) tuple type should parse; got: " + ast.errors());
        assertTrue(ast.asf().infer(), "inference should succeed");
    }

    // ─────────── Problem 3: tuple-variant payload + match unpack ─────────────

    @Test
    void tuple_variant_function_payload_can_be_unpacked_and_called() {
        String code = """
                outline Operator =
                    Map(String -> String,)
                  | Filter(String -> Bool,)
                  | Source;

                let op = Map(s -> s + "!");
                match op {
                    Map(f)    -> f("ok"),
                    Filter(p) -> if (p("ok")) "yes" else "no",
                    Source    -> "source"
                }
                """;
        Value v = RunnerHelper.run(code);
        assertEquals("\"ok!\"", v.toString());
    }

    @Test
    void tuple_variant_preserves_chunk_function_payload_type() {
        String code = """
                outline Chunk = Delta{text:String} | Done | Error{reason:String};
                outline Operator =
                    Map(Chunk -> Chunk,)
                  | Source;

                let chunk = Delta{text="hi"};
                let map_op    = Map(c -> match c { Delta{text as t} -> Delta{text=t + "!"}, _ -> c });
                match map_op {
                    Map(f) -> f(chunk),
                    _      -> Done
                }
                """;
        Value v = RunnerHelper.run(code);
        assertEquals("Delta{text = hi!}", v.toString());
    }

    @Test
    void tuple_variant_preserves_filter_payload_type() {
        String code = """
                outline Chunk = Delta{text:String} | Done | Error{reason:String};
                outline Operator = Filter(Chunk -> Bool,) | Source;
                let chunk = Delta{text="hi"};
                let filter_op = Filter(c -> match c { Delta{text as t} -> t == "hi", _ -> false });
                match filter_op {
                    Filter(p) -> if (p(chunk)) "filter:yes" else "filter:no",
                    _         -> "filter:other"
                }
                """;
        Value v = RunnerHelper.run(code);
        assertEquals("\"filter:yes\"", v.toString());
    }

    @Test
    void single_item_variant_payload_without_trailing_comma_reports_inference_error() {
        String code = """
                outline Tmp = Source | Map(? -> ?);
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(),
                "`Map(? -> ?)` remains syntactically legal so diagnostics come from inference; got: " + ast.errors());
        ast.asf().infer();
        assertTrue(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.INFER_ERROR),
                "missing tuple comma should report an inference error; got: " + ast.errors());
    }

    @Test
    void generic_option_alias_with_single_item_tuple_variant_projects_in_steps() {
        String code = """
                outline Operator = Source | Map(? -> ?,);
                outline Operator_2 = <a,b>(Source | Map(a -> b,));
                outline Operator_3 = Operator_2<String>;
                outline Operator_4 = Operator_3<Int>;
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(),
                "generic option alias syntax should parse; got: " + ast.errors());
        assertTrue(ast.asf().infer(),
                "generic option alias projection should infer; got: " + ast.errors());
        assertNotNull(ast.symbolEnv().lookupAll("Operator_4"),
                "projected alias Operator_4 should be defined");
        assertEquals("Source|Map(String->Integer)", ast.symbolEnv().lookupAll("Operator_4").outline().toString());
    }

    @Test
    void misspelled_generic_alias_reports_outline_not_found_before_not_referable() {
        String code = """
                outline Opertor_2 = <a,b>(Source | Map(a -> b,));
                outline Operator_3 = Operator_2<String>;
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(),
                "misspelled alias case should parse so inference can report the name error; got: " + ast.errors());
        ast.asf().infer();
        assertTrue(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.OUTLINE_NOT_FOUND),
                "undefined Operator_2 should report OUTLINE_NOT_FOUND; got: " + ast.errors());
        assertFalse(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.NOT_REFER_ABLE),
                "undefined Operator_2 should not cascade into NOT_REFER_ABLE; got: " + ast.errors());
    }

    @Test
    void tuple_variant_preserves_round_operator_payload_types() {
        String code = """
                outline ChunkSession = {
                    step: Int,
                    question: String,
                    last_answer: String
                };
                outline Operator =
                    Loop((String, ChunkSession) -> String,)
                  | Until(ChunkSession -> Bool,)
                  | Source;
                let session = ChunkSession{step=1, question="q", last_answer="a"};
                let loop_op = Loop((answer, s) -> s.question + ":" + answer);
                match loop_op {
                    Loop(f) -> "loop:" + f("ans", session),
                    _       -> "loop:other"
                }
                """;
        Value v = RunnerHelper.run(code);
        assertEquals("\"loop:q:ans\"", v.toString());
    }

    @Test
    void tuple_variant_preserves_until_payload_type_and_bare_branch() {
        String code = """
                outline ChunkSession = {
                    step: Int,
                    question: String,
                    last_answer: String
                };
                outline Operator =
                    Until(ChunkSession -> Bool,)
                  | Source;
                let session = ChunkSession{step=1, question="q", last_answer="a"};
                let until_op = Until(s -> s.step > 0);
                match until_op {
                    Until(p) -> if (p(session)) "until:yes" else "until:no",
                    Source   -> "source"
                }
                """;
        Value v = RunnerHelper.run(code);
        assertEquals("\"until:yes\"", v.toString());
    }

    @Test
    void generic_operator_recursive_push_infers_without_timeout() {
        String code = """
                outline Operator = <a,b>(Source | Map(a -> b,));
                let push = (stage, value, sink) -> {
                    let op = stage._operator;
                    match op {
                        Source -> sink(value),
                        Map(f) -> push(stage._upstream, value, x -> sink(f(x)))
                    }
                };
                """;
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            AST ast = parse(code);
            assertTrue(ast.errors().isEmpty(), "unexpected parse errors: " + ast.errors());
            ast.asf().infer();
            ast.meta().toMap();
            ast.asf().interpret();
        });
    }

    @Test
    void default_duplicate_field_must_satisfy_existing_field_type() {
        String code = """
                outline Box = { value: Int };
                outline BadBox = Box{ value: "not an int" };
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(), "unexpected parse errors: " + ast.errors());
        ast.asf().infer();
        assertTrue(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.OUTLINE_MISMATCH),
                "duplicate field should reject incompatible type; got: " + ast.errors());
    }

    @Test
    void entity_extension_duplicate_field_modes_are_explicit() {
        String code = """
                outline Base = { value: Number };
                outline Narrow = Base{ value: Int };
                outline Wide = Narrow{ override value: Number };
                outline OverloadSameBase = Base{ overload value: Int };
                outline OverloadWide = Narrow{ overload value: Number };
                """;
        AST ast = parse(code);
        assertTrue(ast.errors().isEmpty(), "unexpected parse errors: " + ast.errors());
        assertTrue(ast.asf().infer(), "field merge modes should infer; got: " + ast.errors());
        assertEquals("{value: Integer}", ast.symbolEnv().lookupAll("Narrow").outline().toString());
        assertEquals("{value: Number}", ast.symbolEnv().lookupAll("Wide").outline().toString());
        assertEquals("{value: Number}", ast.symbolEnv().lookupAll("OverloadSameBase").outline().toString());
        assertEquals("{value: Number}", ast.symbolEnv().lookupAll("OverloadWide").outline().toString());
    }
}
