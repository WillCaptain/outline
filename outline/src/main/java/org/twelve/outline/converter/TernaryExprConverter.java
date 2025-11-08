package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.conditions.Arm;
import org.twelve.gcp.node.expression.conditions.Consequence;
import org.twelve.gcp.node.expression.conditions.IfArm;
import org.twelve.gcp.node.expression.conditions.TernaryExpression;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class TernaryExprConverter extends Converter {
    public TernaryExprConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<ParseNode> nodes = ((NonTerminalNode) source).nodes();
//        Selections ifs = new Selections(SELECTION_TYPE.TERNARY, ast);
        TernaryExpression ifs = new TernaryExpression(ast);
        int i = 0;
        Expression test = cast(converters.get(nodes.get(0).name()).convert(ast, nodes.get(i)));
        Consequence consequence = cast(converters.get(Constants.CONSEQUENCE).convert(ast, nodes.get(2)));
        ifs.addArm(new IfArm(test, consequence));
        consequence = cast(converters.get(Constants.CONSEQUENCE).convert(ast, nodes.get(4)));
        ifs.addArm(new IfArm(consequence));
        return ifs;
    }
}
