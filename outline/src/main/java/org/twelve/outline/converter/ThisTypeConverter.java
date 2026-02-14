package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.typeable.ThisTypeNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

public class ThisTypeConverter extends Converter {
    public ThisTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return new ThisTypeNode(ast);
    }
}
