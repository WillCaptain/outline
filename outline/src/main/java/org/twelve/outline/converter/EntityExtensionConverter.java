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

public class EntityExtensionConverter extends Converter {
    public EntityExtensionConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode host = ((NonTerminalNode) source).node(0);
        NonTerminalNode extension = cast(((NonTerminalNode) source).node(1));
        List<MemberNode> members = convertMembers(ast,extension);
        return new EntityNode(members,converters.get(host.name()).convert(ast,host));
    }

    protected List<MemberNode> convertMembers(AST ast, NonTerminalNode entity){
        ParseNode[] properties = entity.nodes().stream().filter(n -> !"{,}".contains(n.lexeme())).toArray(ParseNode[]::new);
        List<MemberNode> members = new ArrayList<>();
        for (ParseNode property : properties) {
            members.add(cast(converters.get(property.name()).convert(ast,property)));
        }
        return members;
    }
}
