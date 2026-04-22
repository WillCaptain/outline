package org.twelve.outline;

import java.util.Collections;
import java.util.List;

/**
 * Thrown by {@link OutlineParser#parseResilient(org.twelve.gcp.ast.ASF, String)}
 * only when the partially-recovered parse tree is too broken for
 * {@link GCPConverter} to build an {@link org.twelve.gcp.ast.AST} at all.
 *
 * <p>Carries the syntax errors that the parser <em>did</em> manage to collect,
 * so diagnostics-oriented callers (IDE validators, language servers) can
 * still report meaningful markers instead of an opaque stack trace.
 *
 * <p>Note: ordinary grammar syntax errors never surface as this exception —
 * they are attached to the AST via {@link org.twelve.gcp.ast.AST#syntaxErrors()}.
 */
public class ResilientParseException extends RuntimeException {

    private final List<String> syntaxErrors;

    public ResilientParseException(List<String> syntaxErrors, Throwable cause) {
        super(summarize(syntaxErrors, cause), cause);
        this.syntaxErrors = syntaxErrors == null ? Collections.emptyList() : List.copyOf(syntaxErrors);
    }

    public List<String> syntaxErrors() {
        return this.syntaxErrors;
    }

    private static String summarize(List<String> errs, Throwable cause) {
        int n = errs == null ? 0 : errs.size();
        String causeMsg = cause == null ? "unknown" :
                cause.getClass().getSimpleName()
                        + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
        return "resilient parse failed after collecting " + n + " syntax error(s); cause=" + causeMsg;
    }
}
