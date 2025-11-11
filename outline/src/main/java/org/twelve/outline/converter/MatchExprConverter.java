package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.conditions.Consequence;
import org.twelve.gcp.node.expression.conditions.MatchArm;
import org.twelve.gcp.node.expression.conditions.MatchExpression;
import org.twelve.gcp.node.expression.conditions.MatchTest;
import org.twelve.gcp.node.statement.ReturnStatement;
import org.twelve.gcp.node.statement.Statement;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class MatchExprConverter extends Converter {
    public MatchExprConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode parent = cast(source);
        ParseNode node = parent.node(1);
        MatchExpression matcher = new MatchExpression(ast, cast(converters.get(node.name()).convert(ast, node)));
        parent.nodes().stream().filter(n -> n.name().equals(Constants.MATCH_ARM)).forEach(n -> {
            matcher.addArm(this.convertArm(ast, n));
        });
        return matcher;
    }

    private MatchArm convertArm(AST ast, ParseNode node) {
        NonTerminalNode arm = cast(node);
        Expression pattern = cast(converters.get(arm.node(0).name()).convert(ast, arm.node(0)));
        Expression condition = null;
        if (arm.node(1).name().equals("match_if")) {
            ParseNode c = ((NonTerminalNode) arm.node(1)).node(1);
            condition = cast(converters.get(c.name()).convert(ast, c));
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
