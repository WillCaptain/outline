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
import org.twelve.outline.wrappernode.EntityFieldWithDefaultWrapper;

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
        ParseNode first = nodes.removeFirst();

        List<ReferenceNode> refs = new ArrayList<>();
        if (first.name().equals(Constants.REFERENCE_TYPE)) {
            refs = convertReferences(converters, cast(first), ast);
        }

        EntityTypeNode entityTypeNode = refs.isEmpty() ? null : null; // built below after collecting fields

        List<Variable> fields = new ArrayList<>();
        List<Node> defaultNodes = new ArrayList<>(); // parallel list: null or default-value AST node

        for (ParseNode n : nodes) {
            if (("{<(,)>}").contains(n.lexeme())) continue;

            Node converted = converters.get(n.name()).convert(ast, n);

            if (converted instanceof EntityFieldWithDefaultWrapper dfw) {
                // alias: "alice"  →  Variable with no declared type; default stored separately
                fields.add(new Variable(dfw.field(), false, null));
                defaultNodes.add(dfw.defaultValueNode());
            } else if (converted instanceof ArgumentWrapper aw) {
                fields.add(new Variable(aw.argument(), false, aw.typeNode()));
                defaultNodes.add(null);
            } else {
                fields.add(new Variable(cast(converted), false, null));
                defaultNodes.add(null);
            }
        }

        EntityTypeNode result = fields.isEmpty() ? new EntityTypeNode(ast) : new EntityTypeNode(refs, fields);

        // register default values into the EntityTypeNode
        for (int i = 0; i < fields.size(); i++) {
            Node def = defaultNodes.get(i);
            if (def != null) {
                result.addDefault(fields.get(i).name(), def);
            }
        }

        return result;
    }
}
