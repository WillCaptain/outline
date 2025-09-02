package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.typeable.ArrayTypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ArrayTypeConverter implements Converter {
    private final Map<String, Converter> converters;

    public ArrayTypeConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        if(((NonTerminalNode)source).nodes().size()==2){
            return new ArrayTypeNode(ast);
        }else{
            ParseNode itemType = ((NonTerminalNode) source).node(1);
            return new ArrayTypeNode(ast,cast(converters.get(Constants.COLON_+itemType.name()).convert(ast,itemType,null)));
        }
    }
}
