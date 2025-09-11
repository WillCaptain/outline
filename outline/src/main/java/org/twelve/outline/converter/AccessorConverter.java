package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.accessor.ArrayAccessor;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.msll.util.Tool.cast;

public class AccessorConverter extends Converter {
    public AccessorConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode arrayOrMap = cast(source);
        return new ArrayAccessor(ast,cast(related),cast(converters.get(arrayOrMap.node(1).name()).convert(ast,arrayOrMap.node(1))));
    }
}
