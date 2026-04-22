package org.twelve.outlineaipp;

import org.springframework.stereotype.Service;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.exception.GCPError;
import org.twelve.gcp.interpreter.OutlineInterpreter;
import org.twelve.gcp.interpreter.value.Value;
import org.twelve.outline.OutlineParser;
import org.twelve.outline.ResilientParseException;
import org.twelve.outline.diagnostic.OutlineSyntaxDiagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared runtime bridge to the {@code outline} language core.
 *
 * <p>One {@link OutlineParser} instance is kept alive for the lifetime of the
 * Spring context: its constructor warms the shared grammar tables (one-off
 * ~seconds), and every tool call would otherwise pay that cost or — worse —
 * race on the static initialiser. Under Spring's default singleton scope
 * the warm-up happens exactly once at boot.
 *
 * <p>All three entrypoints ({@link #parse}, {@link #infer}, {@link #interpret})
 * are <em>resilient</em> — they never throw on malformed input. Errors are
 * surfaced as structured strings so the LLM can reason about them directly
 * without special host-side plumbing. Every error string follows one of:
 * <pre>
 *   "line L:C message"          // syntax / normalized parser diagnostic
 *   "[error] ... @line L:C"     // type / inference diagnostic (GCPError#toString)
 * </pre>
 */
@Service
public class OutlineRuntimeService {

    private final OutlineParser parser = new OutlineParser();

    // ── result records ────────────────────────────────────────────────────────

    /** Output of {@link #parse}. */
    public record ParseResult(boolean ok, List<String> errors) {}

    /**
     * Output of {@link #infer}. Parse errors are kept separate from inference
     * errors so the LLM can tell "I had a typo" from "I had a type mismatch" —
     * those require very different fixes.
     */
    public record InferResult(boolean ok,
                              List<String> syntax_errors,
                              List<String> infer_errors) {}

    /**
     * Output of {@link #interpret}. {@code result} is present only when there
     * are no syntax/infer errors and the interpreter ran to completion;
     * otherwise it is {@code null} and {@code ok == false}.
     */
    public record InterpretResult(boolean ok,
                                  List<String> syntax_errors,
                                  List<String> infer_errors,
                                  String runtime_error,
                                  Map<String, Object> result) {}

    // ── parse ─────────────────────────────────────────────────────────────────

    public ParseResult parse(String code) {
        ParseStage stage = parseInto(new ASF(), code);
        return new ParseResult(stage.ast != null && stage.errors.isEmpty(), stage.errors);
    }

    /**
     * Resilient parse + syntax-error normalization. The returned
     * {@link ParseStage} is the single source of truth for downstream stages
     * (infer, interpret) so they all report errors the same way.
     */
    private ParseStage parseInto(ASF asf, String code) {
        if (code == null) return ParseStage.fatal(List.of("code: null"));
        List<String> syntax = new ArrayList<>();
        AST ast;
        try {
            ast = parser.parseResilient(asf, code);
            if (ast.syntaxErrors() != null) {
                syntax.addAll(normalizeSyntaxErrors(ast.syntaxErrors(), code));
            }
        } catch (ResilientParseException e) {
            syntax.addAll(normalizeSyntaxErrors(e.syntaxErrors(), code));
            return new ParseStage(null, syntax);
        } catch (RuntimeException e) {
            syntax.add("parser crashed: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            return new ParseStage(null, syntax);
        }
        return new ParseStage(ast, syntax);
    }

    /**
     * Turns raw MSLL messages into the short, LLM-friendly form produced by
     * {@link OutlineSyntaxDiagnostics}. We deliberately discard the multi-
     * line "possible productions" noise that MSLL emits — it is useless for
     * fix suggestions and balloons the context window.
     */
    private List<String> normalizeSyntaxErrors(List<String> raw, String code) {
        List<Map<String, Object>> markers = new ArrayList<>();
        for (String msg : raw) {
            markers.add(OutlineSyntaxDiagnostics.parseMarker(msg, code));
        }
        List<Map<String, Object>> normalized = OutlineSyntaxDiagnostics.normalizeMarkers(markers);
        List<String> out = new ArrayList<>(normalized.size());
        for (Map<String, Object> m : normalized) {
            out.add(formatMarker(m));
        }
        return out;
    }

    private String formatMarker(Map<String, Object> m) {
        Object line = m.getOrDefault("startLine", "?");
        Object col  = m.getOrDefault("startColumn", "?");
        Object msg  = m.getOrDefault("message", "syntax error");
        return "line " + line + ":" + col + " " + msg;
    }

    /** Internal intermediate so infer/interpret don't re-run the parser. */
    private record ParseStage(AST ast, List<String> errors) {
        static ParseStage fatal(List<String> errs) { return new ParseStage(null, errs); }
    }

    // ── infer ─────────────────────────────────────────────────────────────────

    public InferResult infer(String code) {
        ASF asf = new ASF();
        ParseStage stage = parseInto(asf, code);
        List<String> syntax = stage.errors;
        List<String> inferErrs = new ArrayList<>();

        // Only run inference if we got a non-null AST. Some resilient parses
        // produce so little structure that inference would tautologically
        // succeed on the empty forest — not a useful signal to the LLM.
        if (stage.ast != null) {
            try {
                asf.infer();
            } catch (RuntimeException e) {
                // Node-level errors are usually already attached to ASTs;
                // anything that escapes is reported as a single inference
                // error so the LLM knows something went wrong.
                inferErrs.add("[error] inference crashed: "
                        + e.getClass().getSimpleName()
                        + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            }
            for (GCPError ge : asf.allErrors()) {
                inferErrs.add(ge.toString());
            }
        }
        boolean ok = syntax.isEmpty() && inferErrs.isEmpty() && stage.ast != null;
        return new InferResult(ok, syntax, inferErrs);
    }

    // ── interpret ─────────────────────────────────────────────────────────────

    public InterpretResult interpret(String code) {
        ASF asf = new ASF();
        ParseStage stage = parseInto(asf, code);
        List<String> syntax = stage.errors;
        List<String> inferErrs = new ArrayList<>();

        if (stage.ast == null || !syntax.isEmpty()) {
            return new InterpretResult(false, syntax, inferErrs, null, null);
        }
        try {
            asf.infer();
        } catch (RuntimeException e) {
            inferErrs.add("[error] inference crashed: "
                    + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : ""));
        }
        for (GCPError ge : asf.allErrors()) inferErrs.add(ge.toString());
        if (!inferErrs.isEmpty()) {
            // Refuse to execute code that failed type-checking. Running it
            // would either crash in unpredictable ways or, worse, produce a
            // misleading "result" based on partially-inferred stubs.
            return new InterpretResult(false, syntax, inferErrs, null, null);
        }

        try {
            Value v = new OutlineInterpreter().run(asf);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("display", v == null ? "null" : v.display());
            result.put("type",    v == null ? "null" : v.getClass().getSimpleName());
            // unwrap() may return complex graph objects; only pass the scalar
            // subset that is JSON-safe, and fall back to the display string
            // otherwise. This keeps the tool payload small and serializable.
            Object raw = v == null ? null : v.unwrap();
            if (raw == null || raw instanceof String
                    || raw instanceof Number || raw instanceof Boolean) {
                result.put("value", raw);
            } else {
                result.put("value", v.display());
            }
            return new InterpretResult(true, syntax, inferErrs, null, result);
        } catch (RuntimeException e) {
            String msg = "[runtime] " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : "");
            return new InterpretResult(false, syntax, inferErrs, msg, null);
        }
    }
}
