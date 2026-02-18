package org.twelve.outline;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.EntityTypeNode;
import org.twelve.gcp.node.expression.typeable.ExtendTypeNode;
import org.twelve.gcp.node.expression.typeable.ReferenceCallTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.gcp.outline.projectable.Reference;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.common.Tool;
import org.twelve.outline.converter.Converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ExtendOutlineConverter extends Converter {
    public ExtendOutlineConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<ReferenceNode> refs = new ArrayList<>();
        ReferenceCallTypeNode refArgs = null;
        List<ParseNode> nodes = ((NonTerminalNode) source).nodes();
        ParseNode node = nodes.removeFirst();
        if(node.name().equals(Constants.REFERENCE_TYPE)){
            refs = Tool.convertReferences(converters,cast(node),ast);
            node = nodes.removeFirst();
        }
        TypeNode base = cast(converters.get(Constants.COLON_+ node.name()).convert(ast,node));
        node = nodes.removeFirst();
        if(node.name().equals(Constants.REFERENCE_CALL)){
            refArgs = cast(converters.get(Constants.COLON_+node.name()).convert(ast,node,base));
            node = nodes.removeFirst();
        }
        EntityTypeNode extension = cast(converters.get(Constants.COLON_+node.name()).convert(ast,node));
        return new ExtendTypeNode(ast,refs,base,refArgs,extension);
    }
}
