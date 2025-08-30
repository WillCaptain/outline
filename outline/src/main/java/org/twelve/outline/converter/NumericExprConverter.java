package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.inference.operator.*;
import org.twelve.gcp.node.expression.BinaryExpression;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.operator.OperatorNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class NumericExprConverter implements Converter{
    private final Map<String, Converter> converters;

    public NumericExprConverter (Map<String, Converter> converters){
        this.converters = converters;
    }
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode num = cast(source);
        Expression x = cast(converters.get(num.node(0).name()).convert(ast,num.node(0),null));
        Expression y = cast(converters.get(num.node(2).name()).convert(ast,num.node(2),null));
        return new BinaryExpression(x, y, new OperatorNode<>(ast,BinaryOperator.parse(num.node(2).lexeme())));
    }
}
