package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.TupleNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

public class TupleConverter extends Converter{
    public TupleConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        Expression[] values = ((NonTerminalNode) source).nodes().stream().filter(n ->
                        !n.lexeme().equals("(") && !n.lexeme().equals(")") && !n.lexeme().equals(","))
                .map(n->converters.get(n.name()).convert(ast,n)).toArray(Expression[]::new);
        return new TupleNode(ast,values);
    }
}
