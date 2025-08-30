package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.common.SELECTION_TYPE;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.conditions.Arm;
import org.twelve.gcp.node.expression.conditions.Consequence;
import org.twelve.gcp.node.expression.conditions.Selections;
import org.twelve.gcp.node.statement.ExpressionStatement;
import org.twelve.gcp.node.statement.ReturnStatement;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class IfConverter implements Converter {
    private final Map<String, Converter> converters;

    public IfConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<ParseNode> nodes = ((NonTerminalNode) source).nodes();
        Selections ifs = new Selections(SELECTION_TYPE.IF, ast);
        int i = 0;
        if (nodes.get(i).name().equals(Constants.If)) {
            i += 2;
            Expression test = cast(converters.get(nodes.get(i).name()).convert(ast, nodes.get(i), null));
            i += 2;
            Consequence consequence = cast(converters.get(Constants.Consequence).convert(ast, nodes.get(i), null));
            ifs.addArm(new Arm(test, consequence));
            i++;
        }
        if (nodes.get(i).name().equals(Constants.Else)) {
            i++;
            Node other = converters.get(nodes.get(i).name()).convert(ast, nodes.get(i), null);
            if (other instanceof Selections) {
                ifs.arms().addAll(((Selections) other).arms());
            } else {
                Consequence consequence = cast(converters.get(Constants.Consequence).convert(ast, nodes.get(i), null));
                ifs.arms().add(new Arm(consequence));
            }
        }

        if (related != null) {
           if(related.parent().nodes().getLast()==related) {
               related.addNode(new ReturnStatement(ifs));
           }else{
               related.addNode(new ExpressionStatement(ifs));
           }
        }

        return ifs;
    }
}
