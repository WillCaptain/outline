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
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.gcp.node.function.Argument;
import org.twelve.gcp.node.function.FunctionNode;
import org.twelve.gcp.node.operator.OperatorNode;
import org.twelve.gcp.node.statement.ReturnStatement;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class FunctionConverter implements Converter{
    private final Map<String, Converter> converters;

    public FunctionConverter (Map<String, Converter> converters){
        this.converters = converters;
    }
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode func = cast(source);
//        NonTerminalNode originArgs = cast(func.node(0));
        ParseNode[] originArgs = ((NonTerminalNode) func.node(0)).nodes().stream().filter(n -> !"(,)".contains(n.lexeme())).toArray(ParseNode[]::new);
        Argument[] args = new Argument[Math.round(originArgs.length)];
        for(int i=0; i<originArgs.length;i++){
            Identifier arg = null;
            TypeNode typeNode = null;
            if(originArgs[i].name().equals(Constants.ARGUMENT)) {
                NonTerminalNode argument = cast(originArgs[i]);
                arg = cast(converters.get(argument.node(0).name()).convert(ast, argument.node(0), null));
                typeNode = cast(converters.get(argument.node(2).name()).convert(ast, argument.node(2), null));
            }else{
                arg = cast(converters.get(originArgs[i].name()).convert(ast, originArgs[i], null));
            }
            args[i] = new Argument(arg,typeNode);
        }
        ParseNode originBody = func.node(2);
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