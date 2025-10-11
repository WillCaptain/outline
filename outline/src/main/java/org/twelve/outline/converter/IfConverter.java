package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.common.SELECTION_TYPE;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.conditions.Arm;
import org.twelve.gcp.node.expression.conditions.Consequence;
import org.twelve.gcp.node.expression.conditions.Selections;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class IfConverter extends Converter {
    public IfConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<ParseNode> nodes = ((NonTerminalNode) source).nodes();
        Selections ifs = new Selections(SELECTION_TYPE.IF, ast);
        int i = 0;
        if (nodes.get(i).name().equals(Constants.IF)) {
            i += 2;
            Expression test = cast(converters.get(nodes.get(i).name()).convert(ast, nodes.get(i)));
            i += 2;
            Consequence consequence = cast(converters.get(Constants.CONSEQUENCE).convert(ast, nodes.get(i)));
            ifs.addArm(new Arm(test, consequence));
            i++;
        }
        if (nodes.get(i).name().equals(Constants.ELSE)) {
            i++;
            Node other = converters.get(nodes.get(i).name()).convert(ast, nodes.get(i));
            if (other instanceof Selections) {
                for (Arm arm : ((Selections) other).arms()) {
                    ifs.addArm(arm);
                }
//                ifs.arms().addAll(((Selections) other).arms());
            } else {
                Consequence consequence = cast(converters.get(Constants.CONSEQUENCE).convert(ast, nodes.get(i)));
                ifs.addArm(new Arm(consequence));
            }
        }

        return ifs;
//        if (related != null) {
//           if(related.parent().nodes().getLast()==related) {
//               related.addNode(new ReturnStatement(ifs));
//           }else{
//               related.addNode(new ExpressionStatement(ifs));
//           }
//           return null;
//        }else {
//            return ifs;
//        }
    }
}
