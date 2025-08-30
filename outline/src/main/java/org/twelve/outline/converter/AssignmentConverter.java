package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.inference.operator.AssignableOperator;
import org.twelve.gcp.node.operator.OperatorNode;
import org.twelve.gcp.node.statement.Assignment;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class AssignmentConverter implements Converter{
    private final Map<String, Converter> converters;

    public AssignmentConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode left = ((NonTerminalNode)source).node(0);
        AssignableOperator operator = AssignableOperator.from(((NonTerminalNode)source).node(1).lexeme());
        ParseNode right = ((NonTerminalNode)source).node(2);
        return new Assignment(cast(converters.get(left.name()).convert(ast,left,null)),
                cast(converters.get(right.name()).convert(ast,right,null)),
                new OperatorNode<>(ast,operator));
    }
}
