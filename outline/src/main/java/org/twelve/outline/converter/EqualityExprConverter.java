package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.inference.operator.BinaryOperator;
import org.twelve.gcp.node.expression.BinaryExpression;
import org.twelve.gcp.node.operator.OperatorNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class EqualityExprConverter extends Converter {
    public EqualityExprConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode origin = cast(source);
        Node left = converters.get(origin.node(0).name()).convert(ast,origin.node(0));
        Node right = converters.get(origin.node(2).name()).convert(ast,origin.node(2));
        BinaryOperator opertor = BinaryOperator.parse(origin.node(1).lexeme());
        BinaryExpression expr = new BinaryExpression(cast(left),cast(right),new OperatorNode<>(ast,opertor));
        return expr;
    }
}
