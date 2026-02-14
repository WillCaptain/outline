package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Variable;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.EntityTypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.wrappernode.ArgumentWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;
import static org.twelve.outline.common.Tool.convertReferences;

public class EntityTypeConverter extends Converter {
    public EntityTypeConverter(Map<String, Converter> converters) {
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

        List<Variable> fields = nodes.stream().filter(n -> !("{<(,)>}").contains(n.lexeme()))
//                        !n.lexeme().equals("{") && !n.lexeme().equals("}") && !n.lexeme().equals(","))
                .map(n -> {
                    Node converted = converters.get(n.name()).convert(ast, n);
                    if (converted instanceof ArgumentWrapper) {
                        return new Variable(((ArgumentWrapper) converted).argument(), false, ((ArgumentWrapper) converted).typeNode());
                    } else {
                        return new Variable(cast(converted), false, null);
                    }
                })
                .toList();
        if (fields.isEmpty()) {
            return new EntityTypeNode(ast);
        } else {
            return new EntityTypeNode(refs, fields);
        }
    }
}
