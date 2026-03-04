package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.AwaitNode;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

/**
 * Converts an {@code await_expression} parse node into an {@link AwaitNode}.
 *
 * <p>Grammar rule:
 * <pre>
 *   await_expression : 'await' factor_expression ;
 * </pre>
 *
 * <p>The {@code await} terminal (node 0) is discarded; the promise expression
 * (node 1) is the expression to be awaited.
 */
public class AwaitConverter extends Converter {

    public AwaitConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode parent = cast(source);
        // node(0) = 'await' terminal, node(1) = factor_expression
        Expression promise = cast(converters.get(parent.node(1).name()).convert(ast, parent.node(1)));
        return new AwaitNode(ast, promise);
    }
}
