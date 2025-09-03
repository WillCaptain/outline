package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.ArrayNode;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ArrayRangeConverter extends Converter {
    public ArrayRangeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode range = cast(source);
        Expression begin = null;
        Expression end = null;
        if(range.nodes().size()==2){
            end = cast(converters.get(range.node(1).name()).convert(ast,range.node(1),null));
        }else{
            begin = cast(converters.get(range.node(0).name()).convert(ast,range.node(0),null));
            end = cast(converters.get(range.node(2).name()).convert(ast,range.node(2),null));

        }
        return new ArrayNode(ast,begin,end,null,null,null);
    }
}
