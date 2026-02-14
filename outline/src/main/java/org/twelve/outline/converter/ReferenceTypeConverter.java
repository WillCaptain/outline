package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.wrappernode.ReferenceWrapper;

import java.util.List;
import java.util.Map;

public class ReferenceTypeConverter extends Converter {
    public ReferenceTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<Node> refs = ((NonTerminalNode) source).nodes().stream()
                .filter(n -> !("{<fx,)>}").contains(n.lexeme()))
                .map(n-> converters.get(n.name()).convert(ast, n)).toList();
        return new ReferenceWrapper(ast,refs);
    }
}
