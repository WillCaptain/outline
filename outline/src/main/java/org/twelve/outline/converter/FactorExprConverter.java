package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.List;
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
        if(factor.nodes().stream().anyMatch(n->n.flag().isAmbiguous())){
            List<ParseNode> nodes = factor.nodes();
            Optional<ParseNode> found = nodes.stream().filter(n -> n.name().equals(Constants.ENTITY)).findFirst();
            if(found.isPresent()){
                NonTerminalNode ent = cast(found.get());
                return converters.get(ent.name()).convert(ast, ent);
            }
            return null;
        }else {
            Node head = converters.get(factor.node(0).name()).convert(ast, factor.node(0));
            NonTerminalNode next = cast(factor.node(1));
            return converters.get(next.explain()).convert(ast, next, head);
        }
    }
}
