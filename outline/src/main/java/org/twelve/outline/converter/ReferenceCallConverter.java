package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.referable.ReferenceCallNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

public class ReferenceCallConverter extends Converter {
    public ReferenceCallConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode originType = ((NonTerminalNode)source).node(1);
        ReferenceCallNode rCall = new ReferenceCallNode((Expression)related,(TypeNode) converters.get(Constants.COLON_+originType.name()).convert(ast,originType));
        return rCall;
    }
}
