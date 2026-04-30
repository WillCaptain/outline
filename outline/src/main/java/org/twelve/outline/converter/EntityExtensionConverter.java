package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.SourceLocation;
import org.twelve.gcp.common.FieldMergeMode;
import org.twelve.gcp.node.expression.EntityNode;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.statement.MemberNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;
import static org.twelve.outline.common.Tool.convertStrToken;

public class EntityExtensionConverter extends Converter {
    public EntityExtensionConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode host = ((NonTerminalNode) source).node(0);
        NonTerminalNode extension = cast(((NonTerminalNode) source).node(1));
        List<MemberNode> members = convertMembers(ast,extension);
        var loc = source.location();
        int col = loc.lineStart() - loc.line().beginIndex();
        return new EntityNode(
                members,
                converters.get(host.name()).convert(ast,host),
                new SourceLocation(loc.start(), loc.end(), loc.line().number() + 1, col));
    }

    protected List<MemberNode> convertMembers(AST ast, NonTerminalNode entity){
        List<MemberNode> members = new ArrayList<>();
        for (ParseNode child : entity.nodes()) {
            collectMember(ast, child, members);
        }
        return members;
    }

    private void collectMember(AST ast, ParseNode child, List<MemberNode> members) {
        // skip punctuation: '{', '}', ','
        if (child instanceof TerminalNode) {
            String lex = child.lexeme();
            if ("{".equals(lex) || "}".equals(lex) || ",".equals(lex)) return;
            // bare ID (shorthand_member collapsed to ID terminal)
            members.add(buildShorthand(ast, (TerminalNode) child, FieldMergeMode.DEFAULT, false));
            return;
        }
        String name = child.name();
        if ("entity_first".equals(name) || "entity_member".equals(name)) {
            for (ParseNode g : ((NonTerminalNode) child).nodes()) {
                collectMember(ast, g, members);
            }
            return;
        }
        if ("shorthand_member".equals(name)) {
            members.add(buildShorthandFromNode(ast, (NonTerminalNode) child));
            return;
        }
        // property_assignment (or anything else convertible)
        members.add(cast(converters.get(name).convert(ast, child)));
    }

    private MemberNode buildShorthandFromNode(AST ast, NonTerminalNode shorthand) {
        FieldMergeMode mergeMode = FieldMergeMode.DEFAULT;
        boolean mutable = false;
        int i = 0;
        if (shorthand.node(i).name().equals(Constants.OVERRIDE)
                || shorthand.node(i).name().equals(Constants.OVERLOAD)) {
            mergeMode = shorthand.node(i).name().equals(Constants.OVERRIDE)
                    ? FieldMergeMode.OVERRIDE : FieldMergeMode.OVERLOAD;
            i++;
        }
        if (shorthand.node(i).name().equals(Constants.LET)
                || shorthand.node(i).name().equals(Constants.VAR)) {
            mutable = shorthand.node(i).name().equals(Constants.VAR);
            i++;
        }
        TerminalNode idNode = (TerminalNode) shorthand.node(i);
        return buildShorthand(ast, idNode, mergeMode, mutable);
    }

    private MemberNode buildShorthand(AST ast, TerminalNode idNode, FieldMergeMode mode, boolean mutable) {
        Identifier name = new Identifier(ast, convertStrToken(idNode.token()));
        Identifier expr = new Identifier(ast, convertStrToken(idNode.token()));
        return new MemberNode(name, null, expr, mutable, mode);
    }
}
