package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.Token;
import org.twelve.gcp.common.NullLiteral;
import org.twelve.gcp.node.expression.LiteralNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;

import java.util.Map;

public class NullLiteralConverter extends Converter {
    public NullLiteralConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        TerminalNode terminal = (TerminalNode) source;
        var loc = terminal.token().location();
        int start = loc.start();
        int line = loc.line().number() + 1;
        int col = loc.lineStart();
        return LiteralNode.parse(ast, new Token<>(NullLiteral.INSTANCE, start, line, col));
    }
}
