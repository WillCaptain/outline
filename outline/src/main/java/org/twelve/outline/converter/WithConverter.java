package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.body.Block;
import org.twelve.gcp.node.expression.body.WithExpression;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class WithConverter extends Converter {
    public WithConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode parent = cast(source);
        Expression resource; //cast(converters.get(parent.node(idx++).name()).convert(ast, parent.node(1)));
        Identifier as = null;
        if(parent.node(1) instanceof NonTerminalNode){
            NonTerminalNode r = cast(parent.node(1));
            NonTerminalNode a = cast(r.node(1));
            resource = cast(converters.get(r.node(0).name()).convert(ast, r.node(0)));
            as = cast(converters.get(a.node(1).name()).convert(ast, a.node(1)));
        }else{
            resource =cast(converters.get(parent.node(1).name()).convert(ast, parent.node(1)));
        }
        Block body = cast(converters.get(parent.node(2).name()).convert(ast, parent.node(2)));
        return new WithExpression(ast, resource, as, body);
    }
}
