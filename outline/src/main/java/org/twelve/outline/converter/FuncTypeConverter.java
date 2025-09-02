package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.body.FunctionBody;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.FunctionTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.gcp.node.function.Argument;
import org.twelve.gcp.node.function.FunctionNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class FuncTypeConverter implements Converter {
    private final Map<String, Converter> converters;

    public FuncTypeConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        //FunctionNode func = new FunctionNode(FunctionBody funcBody, List< ReferenceNode > refs, List< Argument > arguments)
        ParseNode origin_arg = ((NonTerminalNode) source).node(0);
        ParseNode oirgin_body = ((NonTerminalNode) source).node(2);
        Node arg = converters.get(Constants.COLON_+origin_arg.name()).convert(ast,origin_arg,null);
        Node body = converters.get(Constants.COLON_+oirgin_body.name()).convert(ast,oirgin_body,null);
        return new FunctionTypeNode(ast, (TypeNode) body, (TypeNode) arg);
    }
}
