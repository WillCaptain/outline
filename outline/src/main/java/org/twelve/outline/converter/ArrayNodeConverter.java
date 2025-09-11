package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.ArrayNode;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ArrayNodeConverter extends Converter {
    public ArrayNodeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        if(((NonTerminalNode)source).nodes().size()==2){
            return new ArrayNode(ast);
        }
        ParseNode originItems = ((NonTerminalNode)source).node(1);
        Node array = converters.get(originItems.name()).convert(ast,originItems);
        if(array instanceof ArrayNode) return array;
        Expression[] items = new Expression[1];//only one item, array is the only on item
        items[0] = cast(array);
        return new ArrayNode(ast,items);
    }
}
