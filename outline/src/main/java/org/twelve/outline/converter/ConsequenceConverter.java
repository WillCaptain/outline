package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.body.Block;
import org.twelve.gcp.node.expression.conditions.Consequence;
import org.twelve.gcp.node.statement.ExpressionStatement;
import org.twelve.gcp.node.statement.ReturnStatement;
import org.twelve.gcp.node.statement.Statement;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ConsequenceConverter extends Converter{
    public ConsequenceConverter(Map<String, Converter> converters) {
        super(converters);
    }

    public Node convert(AST ast, ParseNode source, Node related) {
        Node converted = converters.get(source.name()).convert(ast,source);
        Consequence consequence = new Consequence(ast);
        if(converted instanceof Block){
            for (Node node : converted.nodes()) {
                consequence.addStatement(cast(node));
            }
        }else if(converted instanceof Expression){
            consequence.addStatement(new ReturnStatement((Expression)converted));
        }else if(converted instanceof Statement){
            consequence.addStatement(cast(converted));
        }
        return consequence;
    }
}
