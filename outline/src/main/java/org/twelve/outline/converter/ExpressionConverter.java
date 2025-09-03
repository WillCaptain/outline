package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.As;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ExpressionConverter extends Converter {
    public ExpressionConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode expression = ((NonTerminalNode) source).node(0);
        ParseNode type = ((NonTerminalNode) source).node(2);
        Expression convertedExpression = cast(converters.get(expression.name()).convert(ast, expression, null));
        TypeNode convertedType = cast(converters.get(Constants.COLON_ + type.name()).convert(ast, type, null));
        return new As(convertedExpression,convertedType);
    }
}
