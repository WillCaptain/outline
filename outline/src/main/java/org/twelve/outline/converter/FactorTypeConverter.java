package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.ReferenceCallTypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.common.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class FactorTypeConverter extends Converter {
    public FactorTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<ParseNode> nodes = new ArrayList<>(((NonTerminalNode) source).nodes());
        ParseNode ref = (nodes.getLast().name().equals(Constants.REFERENCE_CALL)) ? nodes.removeLast() : null;
        Node result;
        nodes.removeIf(n -> "(,)".contains(n.lexeme()));
        ParseNode node = nodes.getFirst();
        Node ret = converters.get(Constants.COLON_ + node.name()).convert(ast, node, related);
        if (nodes.size() == 1) {
            result = ret;
        } else {
            node = nodes.getLast();
            result = converters.get(Constants.COLON_ + node.name()).convert(ast, node, ret);
        }
        if (ref == null) {
            return result;
        } else {
            return converters.get(Constants.COLON_ + ref.name()).convert(ast, ref, result);
//            return new ReferenceCallTypeNode(ast,cast(result),null);
        }
    }
}
