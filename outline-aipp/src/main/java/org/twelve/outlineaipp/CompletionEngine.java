package org.twelve.outlineaipp;

import org.springframework.stereotype.Service;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.meta.FieldMeta;
import org.twelve.gcp.meta.MetaExtractor;
import org.twelve.gcp.node.statement.ExpressionStatement;
import org.twelve.gcp.outline.Outline;
import org.twelve.outline.OutlineParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Member / identifier completion for Outline snippets.
 *
 * <p>This is the algorithmic twin of
 * {@code OntologyWorld#completionsForExpressionWithBinding} used by the
 * entity-graph Monaco editor, re-implemented here against <em>pure</em>
 * Outline (no ontology, no world preamble) so that outline-aipp has no
 * reverse dependency on entitir.
 *
 * <p>Algorithm (mirrors the entitir version — keep in sync when fixing bugs):
 * <ol>
 *   <li>Truncate the buffer to {@code offset} (frontend sends full text plus
 *       cursor position; the suffix after the cursor would otherwise mutate
 *       the parse in unpredictable ways).</li>
 *   <li>Strip trailing {@code '.'} — "s.filter(e -> e.)" is unparseable; the
 *       dot is the completion trigger, not real syntax.</li>
 *   <li>Auto-close any unbalanced {@code (}/{@code [}/{@code \{} so the
 *       resilient parser can actually build an AST for the prefix.</li>
 *   <li>Parse + infer. The inferred AST carries the type info we need.</li>
 *   <li>Locate the receiver's {@link Outline}:
 *       <ul>
 *         <li>If the last token is a bare identifier (not reached via a dot),
 *             look it up by lexeme — this is the only way to resolve lambda
 *             parameters like {@code a} in {@code filter(a -> a.)}.</li>
 *         <li>Otherwise take the outline of the last statement — this handles
 *             chains like {@code employee.department.}.</li>
 *       </ul></li>
 *   <li>Ask {@link MetaExtractor#dotCompletionOf} for the actual members.</li>
 * </ol>
 */
@Service
public class CompletionEngine {

    /** Serialisable completion item — maps 1:1 to the public JSON payload. */
    public record CompletionItem(String label, String type, String kind,
                                  String description, String origin) {}

    private final OutlineParser parser = new OutlineParser();

    /**
     * @param code   full editor buffer (not just the prefix — {@code offset}
     *               controls where completion is requested).
     * @param offset character offset of the cursor; {@code < 0} means end-of-buffer.
     */
    public List<CompletionItem> completionsAt(String code, int offset) {
        if (code == null || code.isEmpty()) return List.of();
        int clipped = offset < 0 ? code.length() : Math.max(0, Math.min(offset, code.length()));
        String prefix = code.substring(0, clipped);

        String stripped = prefix.endsWith(".")
                ? prefix.substring(0, prefix.length() - 1)
                : prefix;
        if (stripped.isBlank()) return List.of();

        String inferCode = closeUnclosedDelimiters(stripped);
        String lastWord  = extractLastIdentifier(stripped);

        try {
            ASF asf = new ASF();
            AST ast = parser.parseResilient(asf, inferCode);
            asf.infer();

            Outline outline = resolveReceiverOutline(ast, stripped, lastWord);
            if (outline == null) return List.of();

            List<FieldMeta> fields = MetaExtractor.dotCompletionOf(outline, asf);
            if (fields == null || fields.isEmpty()) return List.of();

            List<CompletionItem> out = new ArrayList<>(fields.size());
            for (FieldMeta f : fields) {
                out.add(new CompletionItem(
                        f.name(),
                        f.type(),
                        f.isMethod() ? "method" : "property",
                        f.description(),
                        f.origin()));
            }
            return out;
        } catch (RuntimeException ignored) {
            // Completion must never 500. An unresolvable prefix simply yields
            // an empty list — the LLM can then back off to `outline_grammar`
            // or re-read its own code for a different anchor.
            return List.of();
        }
    }

    // ── receiver resolution ───────────────────────────────────────────────────

    private static Outline resolveReceiverOutline(AST ast, String stripped, String lastWord) {
        // Path 1: standalone identifier (including lambda params like `a` in
        // `filter(a -> a)`). Only applies when the identifier is not preceded
        // by a '.' — otherwise we want the chained-expression branch below,
        // because the same lexeme may appear earlier in the AST with a
        // different (and wrong) outline.
        if (!lastWord.isEmpty() && stripped.endsWith(lastWord)) {
            boolean standalone = true;
            if (stripped.length() > lastWord.length()) {
                char preceding = stripped.charAt(stripped.length() - lastWord.length() - 1);
                if (preceding == '.') standalone = false;
            }
            if (standalone) {
                Outline byLex = findOutlineByLexeme(ast.program(), lastWord);
                if (byLex != null) return byLex;
            }
        }

        // Path 2: type of the last statement. Handles chained accesses
        // (e.g. `employee.department`) and method calls (`foo()`).
        var body = ast.program().body().nodes();
        if (body.isEmpty()) return null;
        Node lastStmt = body.get(body.size() - 1);
        if (lastStmt instanceof ExpressionStatement es && !es.expressions().isEmpty()) {
            return es.expressions().get(es.expressions().size() - 1).outline();
        }
        return lastStmt.outline();
    }

    /** DFS in source order; returns the outline of the last node matching {@code target}. */
    private static Outline findOutlineByLexeme(Node root, String target) {
        if (root == null || target == null || target.isEmpty()) return null;
        Outline[] last = {null};
        walk(root, target, last);
        return last[0];
    }

    private static void walk(Node node, String target, Outline[] last) {
        if (target.equals(node.lexeme()) && node.outline() != null) {
            last[0] = node.outline();
        }
        for (Node child : node.nodes()) walk(child, target, last);
    }

    // ── string helpers ────────────────────────────────────────────────────────

    /**
     * Last simple identifier in {@code code}. Skips trailing non-id chars
     * first so {@code "foo("} yields {@code "foo"}; a purely non-id tail
     * (e.g. {@code "foo("}) returns the preceding identifier.
     */
    static String extractLastIdentifier(String code) {
        if (code == null || code.isEmpty()) return "";
        int end = code.length();
        while (end > 0) {
            char c = code.charAt(end - 1);
            if (Character.isLetterOrDigit(c) || c == '_') break;
            end--;
        }
        int start = end;
        while (start > 0) {
            char c = code.charAt(start - 1);
            if (!Character.isLetterOrDigit(c) && c != '_') break;
            start--;
        }
        return start < end ? code.substring(start, end) : "";
    }

    /**
     * Append closing delimiters ({@code )}, {@code ]}, {@code \}}) for any
     * that remain open. String-literal aware so {@code "'('"} does not
     * inflate the paren counter.
     */
    static String closeUnclosedDelimiters(String code) {
        int parens = 0, braces = 0, brackets = 0;
        boolean inString = false;
        char strChar = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (inString) {
                if (c == strChar && (i == 0 || code.charAt(i - 1) != '\\')) inString = false;
            } else {
                switch (c) {
                    case '"', '\'' -> { inString = true; strChar = c; }
                    case '('       -> parens++;
                    case ')'       -> { if (parens > 0) parens--; }
                    case '{'       -> braces++;
                    case '}'       -> { if (braces > 0) braces--; }
                    case '['       -> brackets++;
                    case ']'       -> { if (brackets > 0) brackets--; }
                }
            }
        }
        if (parens == 0 && braces == 0 && brackets == 0) return code;
        StringBuilder sb = new StringBuilder(code);
        for (int i = 0; i < brackets; i++) sb.append(']');
        for (int i = 0; i < braces; i++)   sb.append('}');
        for (int i = 0; i < parens; i++)   sb.append(')');
        return sb.toString();
    }
}
