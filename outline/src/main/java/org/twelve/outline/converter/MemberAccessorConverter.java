package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.LiteralNode;
import org.twelve.gcp.node.expression.accessor.MemberAccessor;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class MemberAccessorConverter implements Converter {
    private final Map<String, Converter> converters;

    public MemberAccessorConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode accessor = cast(source);
        Expression host = cast(related);
        Expression member = cast(converters.get(accessor.node(1).name()).convert(ast, accessor.node(1), null));
        if(member instanceof Identifier) {
            host = new MemberAccessor(host, (Identifier) member);
        }
        if(member instanceof LiteralNode<?>){
            host = new MemberAccessor(host,Integer.valueOf(member.lexeme()));

        }
        if (accessor.nodes().size()==3){
            return converters.get(((NonTerminalNode)accessor.node(2)).explain()).convert(ast,accessor.node(2),host);
        }else{
            return host;
        }
    }
}
