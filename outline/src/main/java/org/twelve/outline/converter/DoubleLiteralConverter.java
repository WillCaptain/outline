package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.LiteralNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.convertDoubleToken;

public class DoubleLiteralConverter extends Converter {
    public DoubleLiteralConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return LiteralNode.parse(ast, convertDoubleToken(((TerminalNode) source).token()));
    }
}
