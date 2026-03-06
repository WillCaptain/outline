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

public class NumericExprConverter extends Converter{
    public NumericExprConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode num = cast(source);
        // Build a left-associative binary tree for chained +/- operators.
        // Grammar: numeric_expression : term_expression (('+' | '-') term_expression)*
        // Parse-tree nodes interleave operands and operators: e0 op0 e1 op1 e2 ...
        Expression result = cast(converters.get(num.node(0).name()).convert(ast, num.node(0)));
        int i = 1;
        while (i + 1 < num.nodes().size()) {
            OperatorNode<BinaryOperator> op =
                    new OperatorNode<>(ast, BinaryOperator.parse(num.node(i).lexeme()));
            Expression right = cast(converters.get(num.node(i + 1).name()).convert(ast, num.node(i + 1)));
            result = new BinaryExpression(result, right, op);
            i += 2;
        }
        return result;
    }
}
