package org.twelve.outline.playground.model;

import java.util.List;

public record CompileResponse(
        List<SymbolInfo>    symbols,
        List<DiagnosticInfo> diagnostics,
        String output,
        List<ConsoleEntry>  consoleLogs,
        boolean hasParseError,
        long inferenceMs,
        long executionMs
) {}
