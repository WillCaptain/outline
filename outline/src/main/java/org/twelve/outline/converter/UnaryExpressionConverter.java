package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.inference.operator.UnaryOperator;
import org.twelve.gcp.node.expression.UnaryExpression;
import org.twelve.gcp.node.expression.UnaryPosition;
import org.twelve.gcp.node.operator.OperatorNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class UnaryExpressionConverter extends Converter {
    public UnaryExpressionConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode left = ((NonTerminalNode)source).node(0);
        ParseNode right = ((NonTerminalNode)source).node(1);
        if(UnaryOperator.from(left.lexeme())==null){//right
            return new UnaryExpression(cast(converters.get(left.name()).convert(ast,left,null)),
                    new OperatorNode<>(ast,UnaryOperator.from(right.lexeme())), UnaryPosition.PREFIX);
        }else{//left
            return new UnaryExpression(cast(converters.get(right.name()).convert(ast,right,null)),
                    new OperatorNode<>(ast,UnaryOperator.from(left.lexeme())), UnaryPosition.POSTFIX);
        }
    }
}
