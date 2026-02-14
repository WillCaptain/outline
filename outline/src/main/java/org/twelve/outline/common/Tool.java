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
    public static Token convertStrToken(org.twelve.msll.lexer.Token token) {
        return new Token<>(token.lexeme().replace("\"",""), token.location().start());
    }
    public static Token convertNumToken(org.twelve.msll.lexer.Token token){
        return new Token<>(Tool.parseNumber(token.lexeme()), token.location().start());
    }

    public static Token convertFloatToken(org.twelve.msll.lexer.Token token){
        String lexeme = token.lexeme().substring(0,token.lexeme().length()-2);
        return new Token<>(Float.parseFloat(lexeme), token.location().start());
    }
    public static Token convertDoubleToken(org.twelve.msll.lexer.Token token){
        String lexeme = token.lexeme().substring(0,token.lexeme().length()-2);
        return new Token<>(Double.parseDouble(lexeme), token.location().start());
    }
    public static Token convertIntToken(org.twelve.msll.lexer.Token token){
//        String lexeme = token.lexeme().substring(0,token.lexeme().length()-2);
        return new Token<>(Integer.valueOf(token.lexeme()), token.location().start());
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
