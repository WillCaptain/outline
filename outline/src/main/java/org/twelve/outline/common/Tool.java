package org.twelve.outline.common;

import org.twelve.gcp.ast.Token;

public final class Tool {
    public static <T> T cast(Object obj) {
        // noinspection unchecked
        return (T) obj;
    }
    public static Number parseNumber(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e1) {
            try {
                return Long.valueOf(s);
            } catch (NumberFormatException e2) {
                try {
                    return Double.valueOf(s);
                } catch (NumberFormatException e3) {
                    return new java.math.BigDecimal(s);
                }
            }
        }
    }
    public static Token convertStrToken(org.twelve.msll.lexer.Token token) {
        return new Token<>(token.lexeme().replace("\"",""), token.location().start());
    }
    public static Token convertNumToken(org.twelve.msll.lexer.Token token){
        return new Token<>(Tool.parseNumber(token.lexeme()), token.location().start());
    }
}
