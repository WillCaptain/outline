package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.body.Body;
import org.twelve.gcp.node.expression.body.FunctionBody;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.gcp.node.function.Argument;
import org.twelve.gcp.node.function.FunctionNode;
import org.twelve.gcp.node.statement.ReturnStatement;
import org.twelve.gcp.outline.projectable.Reference;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.wrappernode.ArgumentWrapper;
import org.twelve.outline.wrappernode.ReferenceWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class FunctionConverter implements Converter {
    private final Map<String, Converter> converters;

    public FunctionConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode func = cast(source);
        List<ReferenceNode> refs = new ArrayList<>();
        List<Argument> args = new ArrayList<>();
        int i=0;
        if(func.node(i).name().equals(Constants.REFERENCES)){
            convertArgOrRef(ast,cast(func.node(i)),refs);
            i++;
        }
        convertArgOrRef(ast,cast(func.node(i)),args);
//        ((NonTerminalNode) func.node(0)).nodes().stream().filter(n -> !"(,)".contains(n.lexeme())).forEach(n -> {
//            Identifier ref = null;
//            Identifier arg = null;
//            TypeNode typeNode = null;
//            Node converted = converters.get(n.name()).convert(ast, n, null);
//            if(converted instanceof ReferenceWrapper){
//                ref = ((ReferenceWrapper) converted).argument();
//                typeNode = ((ReferenceWrapper) converted).typeNode();
//            } else if(converted instanceof ArgumentWrapper){
//                arg = ((ArgumentWrapper) converted).argument();
//                typeNode = ((ArgumentWrapper) converted).typeNode();
//            }else{
//                arg = cast(converted);
//            }
//            if(ref==null){
//                args.add(new Argument(arg,typeNode));
//            }else{
//                refs.add(new ReferenceNode(ref,typeNode));
//            }
//        });
        ParseNode originBody = func.node(i+2);
        Node statements = converters.get(originBody.name()).convert(ast, originBody, null);
        FunctionBody body = new FunctionBody(ast);
        if(statements instanceof Body){
            for (Node node : statements.nodes()) {
                body.addStatement(cast(node));
            }
        }else{
            body.addStatement(new ReturnStatement((Expression)statements));
        }

        return FunctionNode.from(body, refs, args);
    }
    private void convertArgOrRef(AST ast,NonTerminalNode parent,List list){
        parent.nodes().stream().filter(n -> !"<(,)>".contains(n.lexeme())).forEach(n -> {
            if(n.name().equals(Constants.FUNC_HEAD)) return;
            Identifier arg;
            TypeNode typeNode = null;
            Node converted = converters.get(n.name()).convert(ast, n, null);
            if(converted instanceof ArgumentWrapper){
                arg = ((ArgumentWrapper) converted).argument();
                typeNode = ((ArgumentWrapper) converted).typeNode();
            }else{
                arg = cast(converted);
            }
            if(parent.name().equals(Constants.REFERENCES)){
                list.add(new ReferenceNode(arg, typeNode));
            }else {
                list.add(new Argument(arg, typeNode));
            }
        });
    }
}
