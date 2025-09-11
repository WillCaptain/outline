package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.ArrayNode;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ArrayExpressionConverter extends Converter {
    public ArrayExpressionConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode expression = cast(source);
        Expression begin = null;
        Expression end = null;
        Expression step = null;
        Expression processor = null;
        Expression condition = null;
        NonTerminalNode range = cast(expression.node(0));
        if(range.nodes().size()==2){
            end = cast(converters.get(range.node(1).name()).convert(ast,range.node(1)));
        }else{
            begin = cast(converters.get(range.node(0).name()).convert(ast,range.node(0)));
            end = cast(converters.get(range.node(2).name()).convert(ast,range.node(2)));

        }
        step = cast(converters.get(expression.node(2).name()).convert(ast,expression.node(2)));
        processor = cast(converters.get(expression.node(4).name()).convert(ast,expression.node(4)));
        condition = cast(converters.get(expression.node(6).name()).convert(ast,expression.node(6)));
        return new ArrayNode(ast,begin,end,step,processor,condition);
    }
}
