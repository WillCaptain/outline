package org.twelve.outline;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.typeable.EntityTypeNode;
import org.twelve.gcp.node.expression.typeable.ExtendTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.converter.Converter;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ExtendOutlineConverter extends Converter {
    public ExtendOutlineConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode parent = cast(source);
        TypeNode base = cast(converters.get(Constants.COLON_+ parent.node(0).name()).convert(ast,parent.node(0)));
        EntityTypeNode extension = cast(converters.get(Constants.COLON_+parent.node(1).name()).convert(ast,parent.node(1)));
        return new ExtendTypeNode(ast,base,extension);
    }
}
