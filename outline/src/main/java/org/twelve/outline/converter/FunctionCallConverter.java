package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.function.FunctionCallNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class FunctionCallConverter extends Converter{
    public FunctionCallConverter (Map<String, Converter> converters){
        super(converters);
    }
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode[] all = ((NonTerminalNode) source).nodes().stream().filter(n -> !"(,)".contains(n.lexeme())).toArray(ParseNode[]::new);
        List<Expression> args = new ArrayList<>();
        NonTerminalNode rest = null;
        for(int i=0; i<all.length;i++){
            if(all[i] instanceof NonTerminalNode && !((NonTerminalNode) all[i]).explain().trim().isEmpty()){
                rest = cast(all[i]);
            }else{
                args.add(cast(converters.get(all[i].name()).convert(ast,all[i])));
            }
        }
        FunctionCallNode call = new FunctionCallNode(cast(related), args.toArray(new Expression[0]));
        if(rest!=null){
            return converters.get(rest.explain()).convert(ast,rest,call);
        }else {
            return call;
        }
    }
}
