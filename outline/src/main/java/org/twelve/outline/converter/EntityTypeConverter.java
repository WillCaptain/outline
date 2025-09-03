package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Variable;
import org.twelve.gcp.node.expression.typeable.EntityTypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.wrappernode.ArgumentWrapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.twelve.outline.common.Tool.cast;

public class EntityTypeConverter extends Converter {
    public EntityTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<Variable> fields = ((NonTerminalNode) source).nodes().stream().filter(n ->
                        !n.lexeme().equals("{") && !n.lexeme().equals("}") && !n.lexeme().equals(","))
                .map(n->{
                    Node converted = converters.get(n.name()).convert(ast,n,null);
                    if(converted instanceof ArgumentWrapper){
                        return new Variable(((ArgumentWrapper) converted).argument(),false,((ArgumentWrapper) converted).typeNode());
                    }else {
                        return new Variable(cast(converted),false,null);
                    }
                })
                .collect(Collectors.toUnmodifiableList());
        if(fields.isEmpty()){
            return new EntityTypeNode(ast);
        }else {
            return new EntityTypeNode(fields);
        }
    }
}
