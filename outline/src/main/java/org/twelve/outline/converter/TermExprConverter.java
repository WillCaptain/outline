package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.inference.operator.BinaryOperator;
import org.twelve.gcp.node.expression.BinaryExpression;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.operator.OperatorNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class TermExprConverter extends Converter{
    public TermExprConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode term = cast(source);
        Expression left = cast(converters.get(term.node(0).name()).convert(ast,term.node(0)));
        Expression right = cast(converters.get(term.node(2).name()).convert(ast,term.node(2)));
        OperatorNode<BinaryOperator> operator = new OperatorNode<>(ast,BinaryOperator.parse(term.node(1).lexeme()));
        return new BinaryExpression(left,right,operator);
    }
}
