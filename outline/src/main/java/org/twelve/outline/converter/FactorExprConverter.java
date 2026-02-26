package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;
import java.util.Optional;

import static org.twelve.outline.common.Tool.cast;

public class FactorExprConverter extends Converter {
    public FactorExprConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode factor = cast(source);
        // entity and block are alternative interpretations of the same { } token span.
        // Prefer entity over block whenever both appear as siblings (ambiguous or structural).
        Optional<ParseNode> entity = factor.nodes().stream()
                .filter(n -> n.name().equals(Constants.ENTITY))
                .findFirst();
        boolean hasBlock = factor.nodes().stream().anyMatch(n -> n.name().equals(Constants.BLOCK));
        if (entity.isPresent() && (entity.get().flag().isAmbiguous() || hasBlock)) {
            NonTerminalNode ent = cast(entity.get());
            return converters.get(ent.name()).convert(ast, ent);
        } else {
            Node head = converters.get(factor.node(0).name()).convert(ast, factor.node(0));
            NonTerminalNode next = cast(factor.node(1));
            return converters.get(next.explain()).convert(ast, next, head);
        }
    }
}
