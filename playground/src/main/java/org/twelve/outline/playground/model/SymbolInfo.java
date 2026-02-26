package org.twelve.outline.playground.model;

/**
 * Type-inference result for a single top-level symbol (variable, function, ADT).
 */
public record SymbolInfo(
        String name,
        String type,
        String kind,    // "let" | "var" | "outline"
        int line,
        int col
) {}
