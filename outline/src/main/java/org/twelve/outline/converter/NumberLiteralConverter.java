package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.Token;
import org.twelve.gcp.node.expression.LiteralNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Tool;

import java.util.Map;

import static org.twelve.outline.common.Tool.convertNumToken;

public class NumberLiteralConverter extends Converter{
    public NumberLiteralConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        int start=0;
        String lexeme = "";
        for (ParseNode node : ((NonTerminalNode) source).nodes()) {
            if(start==0) start = node.location().start();
            lexeme = lexeme + node.lexeme().trim();
        }
        Token token = new Token(Tool.parseNumber(lexeme),start);
        return LiteralNode.parse(ast, token);
    }
}
