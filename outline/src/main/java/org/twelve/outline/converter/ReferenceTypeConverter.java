package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

public class ReferenceTypeConverter extends Converter {
    public ReferenceTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return null;
    }
}
