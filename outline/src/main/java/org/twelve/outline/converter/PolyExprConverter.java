package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.PolyNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class PolyExprConverter implements Converter {
    private final Map<String, Converter> converters;

    public PolyExprConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode next = ((NonTerminalNode) source).node(1);
        String key = next.name();
        if(next instanceof NonTerminalNode && !((NonTerminalNode) next).explain().trim().isEmpty()){
            key = ((NonTerminalNode) next).explain();
        }
        Node convertedNext = converters.get(key).convert(ast, next, null);
        Expression[] rest = null;
        if(convertedNext instanceof PolyNode){
            rest = convertedNext.nodes().toArray(Expression[]::new);
        }else{
            rest = new Expression[1];
            rest[0] = cast(convertedNext);
        }
        return new PolyNode(cast(related),rest);
    }
}
