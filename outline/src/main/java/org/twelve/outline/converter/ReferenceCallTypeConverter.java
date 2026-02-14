package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.referable.ReferenceCallNode;
import org.twelve.gcp.node.expression.typeable.ReferenceCallTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ReferenceCallTypeConverter extends Converter {
    public ReferenceCallTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode refs = (NonTerminalNode) source;
        List<TypeNode> types = new ArrayList<>();
        int i = 1;
        while(true){
            ParseNode originType = ((NonTerminalNode)source).node(i);
            types.add(cast(converters.get(Constants.COLON_+originType.name()).convert(ast,originType)));
            i++;
            if(refs.node(i).lexeme().equals(">")) break;
            i++;
        }
        return new ReferenceCallTypeNode(ast,cast(related),types);
    }
}
