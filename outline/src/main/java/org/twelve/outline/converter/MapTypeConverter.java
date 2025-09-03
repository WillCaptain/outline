package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.typeable.DictTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class MapTypeConverter extends Converter{
    public MapTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode mapType = cast(source);
        TypeNode keyType = cast(converters.get(Constants.COLON_+mapType.node(1).name()).convert(ast,mapType.node(1),null));
        TypeNode valueType = cast(converters.get(Constants.COLON_+mapType.node(3).name()).convert(ast,mapType.node(3),null));
        return new DictTypeNode(ast,keyType,valueType);
    }
}
