package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.SourceLocation;
import org.twelve.gcp.node.expression.EntityNode;
import org.twelve.gcp.node.statement.MemberNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class EntityConverter extends EntityExtensionConverter {
    public EntityConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<MemberNode> members = convertMembers(ast,cast(source));
        var loc = source.location();
        int col = loc.lineStart() - loc.line().beginIndex();
        return new EntityNode(
                members,
                null,
                new SourceLocation(loc.start(), loc.end(), loc.line().number() + 1, col));
    }
}
