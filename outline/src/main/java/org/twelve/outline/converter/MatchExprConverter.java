package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.IsAs;
import org.twelve.gcp.node.expression.conditions.Consequence;
import org.twelve.gcp.node.expression.conditions.MatchArm;
import org.twelve.gcp.node.expression.conditions.MatchExpression;
import org.twelve.gcp.node.expression.conditions.MatchTest;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.gcp.node.statement.ReturnStatement;
import org.twelve.gcp.node.statement.Statement;
import org.twelve.msll.lexer.Token;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;
import static org.twelve.outline.common.Tool.convertStrToken;

public class MatchExprConverter extends Converter {
    public MatchExprConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode parent = cast(source);
        ParseNode subjectNode = parent.node(1);
        MatchExpression matcher = new MatchExpression(ast, cast(converters.get(subjectNode.name()).convert(ast, subjectNode)));
        // The scrutinee identifier token (the `x` in `match x { ... }`) is reused to
        // synthesise pattern/condition AST nodes for `is T` arm sugar (see convertArm).
        Token subjectToken = subjectNode instanceof TerminalNode tn ? tn.token() : null;
        parent.nodes().stream().filter(n -> n.name().equals(Constants.MATCH_ARM)).forEach(n -> {
            matcher.addArm(this.convertArm(ast, n, subjectToken));
        });
        return matcher;
    }

    private MatchArm convertArm(AST ast, ParseNode node, Token subjectToken) {
        NonTerminalNode arm = cast(node);
        ParseNode patternNode = arm.node(0);
        Expression pattern;
        Expression condition = null;
        if (Constants.IS_MATCH_PATTERN.equals(patternNode.name())) {
            // Sugar: `is T -> body`  ≡  `<subject> if(<subject> is T) -> body`.
            // The grammar guarantees `match <ID> { ... }`, so subjectToken is always
            // present here; we synthesise two identifier copies sharing the subject's
            // token (one for the bind pattern, one inside the IsAs guard).
            NonTerminalNode isPattern = cast(patternNode);
            ParseNode typeNode = isPattern.node(1);
            TypeNode convertedType = cast(converters.get(Constants.COLON_ + typeNode.name()).convert(ast, typeNode));
            pattern = new Identifier(ast, convertStrToken(subjectToken));
            condition = new IsAs(new Identifier(ast, convertStrToken(subjectToken)), convertedType);
        } else {
            pattern = cast(converters.get(patternNode.name()).convert(ast, patternNode));
            if (arm.node(1).name().equals("match_if")) {
                ParseNode c = ((NonTerminalNode) arm.node(1)).node(1);
                condition = cast(converters.get(c.name()).convert(ast, c));
            }
        }
        Node body = converters.get(arm.nodes().getLast().name()).convert(ast, arm.nodes().getLast());
        Consequence consequence;
        if (body instanceof Consequence) {
            consequence = cast(body);
        } else {
            consequence = new Consequence(ast);
            if(body instanceof Statement) {
                consequence.addStatement(cast(body));
            }else{
                consequence.addStatement(new ReturnStatement((Expression) body));
            }
        }
        MatchTest test = new MatchTest(ast, pattern, condition);
        return new MatchArm(ast, test, consequence);
    }
}
