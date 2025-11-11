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

public class BinaryExpressionConverter extends Converter {
    public BinaryExpressionConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode rel = cast(source);
        ParseNode left = rel.node(0);
        ParseNode right = rel.node(2);
        ParseNode operator = rel.node(1);
        BinaryExpression expression = new BinaryExpression(cast(converters.get(left.name()).convert(ast,left)),
                cast(converters.get(right.name()).convert(ast,right)),
                new OperatorNode(ast, BinaryOperator.parse(operator.lexeme())));
        return expression;
    }
}
