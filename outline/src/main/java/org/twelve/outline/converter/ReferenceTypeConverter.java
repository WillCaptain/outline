package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

public class ReferenceTypeConverter implements Converter {

    private final Map<String, Converter> converters;

    public ReferenceTypeConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return null;
    }
}
