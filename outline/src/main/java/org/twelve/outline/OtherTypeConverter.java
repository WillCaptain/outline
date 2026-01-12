package org.twelve.outline;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.typeable.OtherTypeNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.converter.Converter;

import java.util.Map;

public class OtherTypeConverter extends Converter {
    public OtherTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return new OtherTypeNode(ast);
    }
}
