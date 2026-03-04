package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.AsyncNode;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

/**
 * Converts an {@code async_expression} parse node into an {@link AsyncNode}.
 *
 * <p>Grammar rule:
 * <pre>
 *   async_expression : 'async' expression ;
 * </pre>
 *
 * <p>The {@code async} terminal (node 0) is discarded; the body expression
 * (node 1) becomes the async body.
 */
public class AsyncConverter extends Converter {

    public AsyncConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode parent = cast(source);
        // node(0) = 'async' terminal, node(1) = expression
        Expression body = cast(converters.get(parent.node(1).name()).convert(ast, parent.node(1)));
        return new AsyncNode(ast, body);
    }
}
