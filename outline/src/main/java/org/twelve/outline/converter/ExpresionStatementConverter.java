package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.statement.ExpressionStatement;
import org.twelve.gcp.node.statement.Statement;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

public class ExpresionStatementConverter implements Converter {
    private final Map<String, Converter> converters;

    public ExpresionStatementConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        Expression[] expressions = ((NonTerminalNode) source).nodes().stream()
                .filter(n -> !",;".contains(n.lexeme()))
                .map(n -> converters.get(n.name()).convert(ast, n, null))
                .toArray(Expression[]::new);
        Statement stat = new ExpressionStatement(expressions);
        related.addNode(stat);
        return stat;
    }
}
