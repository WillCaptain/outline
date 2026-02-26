package org.twelve.outline.common;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.Token;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.outline.converter.Converter;
import org.twelve.outline.wrappernode.ArgumentWrapper;
import org.twelve.outline.wrappernode.ReferenceWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    /**
     * Converts an MSLL lexer token to a GCP {@link Token}, preserving full
     * source-location information (line number and column offset) so that
     * error messages can report {@code "line N:M"} instead of a raw offset.
     *
     * <p>The surrounding double-quotes are stripped and common escape sequences
     * ({@code \n}, {@code \t}, {@code \r}, {@code \\}, {@code \"}) are decoded
     * so that string literals behave intuitively at runtime.
     */
    public static Token convertStrToken(org.twelve.msll.lexer.Token token) {
        var loc = token.location();
        String raw = token.lexeme();
        // Strip only the outermost surrounding double-quote delimiters, then
        // decode escape sequences so that "hello\nworld" → hello + newline + world.
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            raw = raw.substring(1, raw.length() - 1);
        } else {
            raw = raw.replace("\"", "");   // fallback: remove all quotes (old behaviour)
        }
        String decoded = decodeEscapes(raw);
        // MSLL line numbers are 0-based; add 1 for the conventional 1-based display
        return new Token<>(decoded, loc.start(), loc.line().number() + 1, loc.lineStart());
    }

    /**
     * Decodes common Java/C-style string escape sequences.
     * Unrecognised escape sequences are left as-is (backslash retained).
     */
    private static String decodeEscapes(String s) {
        if (!s.contains("\\")) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n'  -> { sb.append('\n'); i++; }
                    case 't'  -> { sb.append('\t'); i++; }
                    case 'r'  -> { sb.append('\r'); i++; }
                    case '"'  -> { sb.append('"');  i++; }
                    case '\'' -> { sb.append('\''); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'u'  -> {
                        // Unicode escape: backslash + u + 4 hex digits
                        if (i + 5 < s.length()) {
                            try {
                                int cp = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                                sb.append((char) cp);
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                    }
                    default -> sb.append(c);  // unrecognised – keep backslash
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static Token convertNumToken(org.twelve.msll.lexer.Token token) {
        var loc = token.location();
        return new Token<>(Tool.parseNumber(token.lexeme()),
                loc.start(), loc.line().number() + 1, loc.lineStart());
    }

    public static Token convertFloatToken(org.twelve.msll.lexer.Token token) {
        String lexeme = token.lexeme().substring(0, token.lexeme().length() - 2);
        var loc = token.location();
        return new Token<>(Float.parseFloat(lexeme),
                loc.start(), loc.line().number() + 1, loc.lineStart());
    }

    public static Token convertDoubleToken(org.twelve.msll.lexer.Token token) {
        String lexeme = token.lexeme().substring(0, token.lexeme().length() - 2);
        var loc = token.location();
        return new Token<>(Double.parseDouble(lexeme),
                loc.start(), loc.line().number() + 1, loc.lineStart());
    }

    public static Token convertIntToken(org.twelve.msll.lexer.Token token) {
        var loc = token.location();
        return new Token<>(Integer.valueOf(token.lexeme()),
                loc.start(), loc.line().number() + 1, loc.lineStart());
    }

    public static List<ReferenceNode> convertReferences(Map<String, Converter> converters, NonTerminalNode node, AST ast) {
        List<ReferenceNode> refs = new ArrayList<>();
        Identifier arg;
        TypeNode typeNode = null;
        ReferenceWrapper converted = cast(converters.get(Constants.COLON_+node.name()).convert(ast, node));
        for (Node ref : converted.refs()) {
            if (ref instanceof ArgumentWrapper) {
                arg = ((ArgumentWrapper) ref).argument();
                typeNode = ((ArgumentWrapper) ref).typeNode();
            } else {
                arg = cast(ref);
            }
            refs.add(new ReferenceNode(arg, typeNode));
        }
        return refs;
    }
}
