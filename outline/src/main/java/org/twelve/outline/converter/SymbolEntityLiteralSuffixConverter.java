package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.SourceLocation;
import org.twelve.gcp.node.expression.EntityNode;
import org.twelve.gcp.node.statement.MemberNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

/**
 * Handles the left-recursive alternative:
 * {@code factor_expression entity                                 # symbol_entity_literal}
 * <p>
 * MSLL expands left-recursion into a suffix rule {@code factor_expression_alpha_N'}
 * whose first child is the trailing {@code entity}, followed by an optional
 * chained alpha-suffix (for further {@code .f()}, {@code (args)}, {@code [i]},
 * or another {@code {...}}). The preceding host expression is supplied as
 * {@code related}.
 */
public class SymbolEntityLiteralSuffixConverter extends EntityExtensionConverter {
    public SymbolEntityLiteralSuffixConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode alpha = cast(source);
        ParseNode entity = alpha.node(0);
        // Entity is at index 0; any further suffix (e.g. .f(), (args)) is at index 1.
        List<MemberNode> members = convertMembers(ast, cast(entity));
        var loc = alpha.location();
        int col = loc.lineStart() - loc.line().beginIndex();
        Node result = new EntityNode(
                members,
                related,
                new SourceLocation(loc.start(), loc.end(), loc.line().number() + 1, col));
        if (alpha.nodes().size() == 2) {
            NonTerminalNode next = cast(alpha.node(1));
            return converters.get(next.explain()).convert(ast, next, result);
        }
        return result;
    }
}
