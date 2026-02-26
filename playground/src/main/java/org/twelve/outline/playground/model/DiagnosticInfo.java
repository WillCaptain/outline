package org.twelve.outline.playground.model;

/**
 * A compilation diagnostic (parse error, type error, or runtime error).
 */
public record DiagnosticInfo(
        String message,
        int line,
        int col,
        String severity   // "error" | "warning" | "info"
) {
    public static DiagnosticInfo error(String message, int line, int col) {
        return new DiagnosticInfo(message, line, col, "error");
    }
    public static DiagnosticInfo error(String message) {
        return new DiagnosticInfo(message, -1, -1, "error");
    }
}
