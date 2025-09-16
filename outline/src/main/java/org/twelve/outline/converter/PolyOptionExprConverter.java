package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.PolyNode;
import org.twelve.msll.exception.ParseErrCode;
import org.twelve.msll.exception.ParseErrorReporter;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class PolyOptionExprConverter extends Converter {
    public PolyOptionExprConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode op = cast(source);
        List<Expression> options = new ArrayList<>();
        int i=1;
        TerminalNode operator = cast(op.node(i));
        while(i<=op.nodes().size()-1){
            if(!operator.lexeme().equals(op.node(i).lexeme())){
                ParseErrorReporter.report(source.parseTree(),op.node(i), ParseErrCode.OPERATOR_MISMATCH,
                        op.node(i).lexeme()+" should be "+operator.lexeme());
            }
            i++;
            options.add(cast(converters.get(op.node(i).name()).convert(ast,op.node(i))));
            i++;
        }
        /*
        ParseNode next = ((NonTerminalNode) source).node(1);
        String key = next.name();
        if(next instanceof NonTerminalNode && !((NonTerminalNode) next).explain().trim().isEmpty()){
            key = ((NonTerminalNode) next).explain();
        }
        Node convertedNext = converters.get(key).convert(ast, next);
        Expression[] rest = null;
        if(convertedNext instanceof PolyNode){
            rest = convertedNext.nodes().toArray(Expression[]::new);
        }else{
            rest = new Expression[1];
            rest[0] = cast(convertedNext);
        }

         */
        return new PolyNode(cast(converters.get(op.node(0).name()).convert(ast,op.node(0))),
                options.toArray(new Expression[0]));
    }
}
