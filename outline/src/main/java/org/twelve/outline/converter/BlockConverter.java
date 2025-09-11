package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.body.Block;
import org.twelve.gcp.node.statement.ReturnStatement;
import org.twelve.gcp.node.statement.Statement;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class BlockConverter extends Converter{
    public BlockConverter(Map<String, Converter> converters){
        super(converters);
    }
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        Block block = new Block(ast);
        NonTerminalNode originBlock = cast(source);
        for(int i=1; i<originBlock.nodes().size()-1; i++){
            Node statement = converters.get(originBlock.node(i).name()).convert(ast,originBlock.node(i));
            if(statement instanceof Statement){
                block.addStatement(cast(statement));
            }else{
                block.addStatement(new ReturnStatement((Expression)statement));
            }
        }
        return block;
    }
}
