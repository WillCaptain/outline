package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.LiteralNode;
import org.twelve.gcp.node.expression.typeable.LiteralTypeNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Tool;

import java.util.Map;

public class LiteralIntTypeConverter implements Converter {
    private final Map<String, Converter> converters;

    public LiteralIntTypeConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return new LiteralTypeNode(LiteralNode.parse(ast, Tool.convertIntToken(((TerminalNode)source).token())));
    }
}
