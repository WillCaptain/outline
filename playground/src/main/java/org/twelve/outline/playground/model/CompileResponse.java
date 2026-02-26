package org.twelve.outline.playground.model;

import java.util.List;

public record CompileResponse(
        List<SymbolInfo>    symbols,
        List<DiagnosticInfo> diagnostics,
        String output,
        boolean hasParseError,
        long inferenceMs,
        long executionMs
) {}
