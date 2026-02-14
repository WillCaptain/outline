package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.FunctionTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.common.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FuncTypeConverter extends Converter {
    public FuncTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<ParseNode> nodes = ((NonTerminalNode) source).nodes();
        NonTerminalNode origin_ref = null;
        //FunctionNode func = new FunctionNode(FunctionBody funcBody, List< ReferenceNode > refs, List< Argument > arguments)
        int index = 0;
        if (nodes.getFirst().name().equals(Constants.REFERENCE_TYPE)) {
            origin_ref = (NonTerminalNode) nodes.getFirst();
            index++;
        }

        ParseNode origin_arg = ((NonTerminalNode) source).node(index);
        ParseNode origin_body = ((NonTerminalNode) source).node(index + 2);

        List<ReferenceNode> ref = origin_ref == null ? new ArrayList<>() : Tool.convertReferences(converters,origin_ref,ast);
        TypeNode[] args = new TypeNode[1];
        args[0] = (TypeNode) converters.get(Constants.COLON_ + origin_arg.name()).convert(ast, origin_arg);
        Node body = converters.get(Constants.COLON_ + origin_body.name()).convert(ast, origin_body);
        return new FunctionTypeNode(ast, (TypeNode) body, ref.toArray(ReferenceNode[]::new),args);
    }
}
