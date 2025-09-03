package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.ArrayNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

public class ArrayNodeConverter extends Converter {
    public ArrayNodeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        if(((NonTerminalNode)source).nodes().size()==2){
            return new ArrayNode(ast);
        }
        ParseNode items = ((NonTerminalNode)source).node(1);
        return converters.get(items.name()).convert(ast,items,null);
    }
}
