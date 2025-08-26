package org.twelve.outline.converters;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.accessor.MemberAccessor;
import org.twelve.gcp.node.function.FunctionCallNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class FactorExprConverter implements Converter {
    private final Map<String, Converter> converters;

    public FactorExprConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode factor = cast(source);
        Node head = converters.get(factor.node(0).name()).convert(ast, factor.node(0), null);
        NonTerminalNode next = cast(factor.node(1));
        return converters.get(next.explain()).convert(ast,next,head);
        //while (next != null) {
//            if (next.explain().equals(Constants.MEMBER_ACCESSOR)) {
//                Expression host = cast(head);
//                Identifier member = cast(converters.get(next.node(1).name()).convert(ast, next.node(1), null));
//                head = new MemberAccessor(host, member);
//                next = next.nodes().size() == 3 ? cast(next.node(2)) : null;
//            }
//            if (next.explain().equals(Constants.FUNCTION_CALL)){
//                return converters.get(next.explain()).convert(ast,next,head);
//            }
//            if(next.explain().equals(Constants.ARRAY_MAP_ACCESSOR)){
//                next = null;
//            }
        //}
//        return head;
    }
}
