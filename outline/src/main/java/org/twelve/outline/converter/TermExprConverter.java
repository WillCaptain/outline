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
        // Build a left-associative binary tree for chained */÷/% operators.
        // Grammar: term_expression : unary_expression (('*' | '/' | '%' | '^') unary_expression)*
        Expression result = cast(converters.get(term.node(0).name()).convert(ast, term.node(0)));
        int i = 1;
        while (i + 1 < term.nodes().size()) {
            OperatorNode<BinaryOperator> op =
                    new OperatorNode<>(ast, BinaryOperator.parse(term.node(i).lexeme()));
            Expression right = cast(converters.get(term.node(i + 1).name()).convert(ast, term.node(i + 1)));
            result = new BinaryExpression(result, right, op);
            i += 2;
        }
        return result;
    }
}
