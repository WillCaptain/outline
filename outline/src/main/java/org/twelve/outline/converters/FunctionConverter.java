package org.twelve.outline.converters;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.Token;
import org.twelve.gcp.inference.operator.BinaryOperator;
import org.twelve.gcp.node.expression.BinaryExpression;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.body.Body;
import org.twelve.gcp.node.expression.body.FunctionBody;
import org.twelve.gcp.node.function.Argument;
import org.twelve.gcp.node.function.FunctionNode;
import org.twelve.gcp.node.operator.OperatorNode;
import org.twelve.gcp.node.statement.ReturnStatement;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class FunctionConverter implements Converter{
    private final Map<String, Converter> converters;

    public FunctionConverter (Map<String, Converter> converters){
        this.converters = converters;
    }
    @Override
    public Node convert(AST ast, ParseNode source, Node targetParent) {
        NonTerminalNode func = cast(source);
        NonTerminalNode originArgs = cast(func.node(0));
        Argument[] args = new Argument[Math.round(originArgs.nodes().size()/2)];
        for(int i=1; i<originArgs.nodes().size()-1;i+=2){
            args[Math.round((i-1)/2)] = new Argument(cast(converters.get(originArgs.node(i).name()).convert(ast,originArgs.node(i),null)));
        }
        NonTerminalNode originBody = cast(func.node(2));
        Node statements = converters.get(originBody.name()).convert(ast, originBody, null);
        FunctionBody body = new FunctionBody(ast);
        if(statements instanceof Body){
            for (Node node : statements.nodes()) {
                body.addStatement(cast(node));
            }
        }else{
            body.addStatement(new ReturnStatement((Expression)statements));
        }
        return FunctionNode.from(body,args);
    }
}
//Identifier x = new Identifier(ast, new Token<>("x", 0));
//Identifier y = new Identifier(ast, new Token<>("y", 0));
//BinaryExpression add = new BinaryExpression(x, y, new OperatorNode<>(ast, BinaryOperator.ADD));
//
////return x+y;
//FunctionBody body = new FunctionBody(ast);
//        body.addStatement(new ReturnStatement(add));
//
//FunctionNode addxy = FunctionNode.from(body, new Argument(new Identifier(ast, new Token<>("x", 0))),
//        new Argument(new Identifier(ast, new Token<>("y", 0))));