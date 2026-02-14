package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.unpack.EntityUnpackNode;
import org.twelve.gcp.node.unpack.UnpackNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class EntityUnpackNodeConverter extends Converter {
    public EntityUnpackNodeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        int i=1;
        NonTerminalNode parent = cast(source);
        EntityUnpackNode ent = new EntityUnpackNode(ast);
        do {
            Identifier id = cast(converters.get(parent.node(i).name()).convert(ast, parent.node(i)));
            i++;
            if (parent.node(i).name().equals(Constants.COLON)) {
                i++;
                UnpackNode nest = cast(converters.get(parent.node(i).name()).convert(ast, parent.node(i)));
                ent.addField(id, nest);
                i++;
            } else if (parent.node(i).name().equals(Constants.AS)) {
                i++;
                Identifier as = cast(converters.get(parent.node(i).name()).convert(ast, parent.node(i)));
                ent.addField(id, as);
                i++;
            } else {
                ent.addField(id);
            }
            if (parent.node(i).name().equals(Constants.COMMA)) i++;
        } while (!parent.node(i).lexeme().equals("}"));
        return ent;
    }
}
