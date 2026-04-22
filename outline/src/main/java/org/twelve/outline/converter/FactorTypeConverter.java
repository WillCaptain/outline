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

        // Extract optional trailing nullable_suffix ('?').
        // The '?' may appear as a NonTerminalNode("nullable_suffix") or as a direct
        // TerminalNode("QUESTION") depending on whether MSLL inlines single-element rules.
        // We identify it by: lexeme == "?" AND it is NOT the sole element (standalone Any type).
        ParseNode nullable = null;
        if (nodes.size() > 1) {
            ParseNode last = nodes.getLast();
            // MSLL may keep nullable_suffix as a non-terminal node (name=nullable_suffix),
            // or inline it as terminal '?' depending on tree simplification stage.
            if ("?".equals(last.lexeme()) || "nullable_suffix".equals(last.name())) {
                nullable = nodes.removeLast();
            }
        }

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
        if (ref != null) {
            result = converters.get(Constants.COLON_ + ref.name()).convert(ast, ref, result);
        }
        if (nullable != null) {
            result = converters.get(Constants.NULLABLE_SUFFIX).convert(ast, nullable, result);
        }
        return result;
    }
}
