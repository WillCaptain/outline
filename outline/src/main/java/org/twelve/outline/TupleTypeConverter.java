package org.twelve.outline;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.TupleTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.converter.Converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;
import static org.twelve.outline.common.Tool.convertReferences;

public class TupleTypeConverter extends Converter {
    public TupleTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<ParseNode> nodes = new ArrayList<>(((NonTerminalNode) source).nodes());
        ParseNode node = nodes.removeFirst();

        List<ReferenceNode> refs = new ArrayList<>();
        if (node.name().equals(Constants.REFERENCE_TYPE)) {
            refs = convertReferences(converters, cast(node), ast);
        }

        List<TypeNode> types = new ArrayList<>();
        nodes.stream().filter(n->!"(,)".contains(n.lexeme())).forEach(n->{
            types.add(cast(converters.get(Constants.COLON_+n.name()).convert(ast,n,null)));
        });
        return new TupleTypeNode(refs,types);
    }
}
