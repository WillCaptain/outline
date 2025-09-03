package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.EntityNode;
import org.twelve.gcp.node.statement.MemberNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class EntityConverter extends Converter {
    public EntityConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode[] properties = ((NonTerminalNode) source).nodes().stream().filter(n -> !"{,}".contains(n.lexeme())).toArray(ParseNode[]::new);
        List<MemberNode> members = new ArrayList<>();
        for (ParseNode property : properties) {
            members.add(cast(converters.get(property.name()).convert(ast,property,null)));
        }
        return new EntityNode(members);
    }
}
