package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.gcp.node.statement.MemberNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class PropertyAssignmentConverter extends Converter {
    public PropertyAssignmentConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode property = cast(source);
        Identifier name = null;
        TypeNode declared = null;
        Expression expression = null;
        Boolean mutable = false;
        int i = 0;
        if (property.node(i).name().equals(Constants.LET) || property.node(i).name().equals(Constants.VAR)) {
            mutable = property.node(i).name().equals(Constants.VAR);
            i++;
        }
        if(property.node(i).name().equals(Constants.ID)){
            name = cast(converters.get(property.node(i).name()).convert(ast,property.node(i),null));
            i++;
        }
        if(property.node(i).name().equals(Constants.COLON)){
            i++;
            declared = cast(converters.get(Constants.COLON_+property.node(i).name()).convert(ast,property.node(i),null));
            i++;

        }
        if(property.node(i).name().equals(Constants.EQUAL)){
           i++;
           expression = cast(converters.get(property.node(i).name()).convert(ast,property.node(i),null));

        }
        return new MemberNode(name, declared, expression, mutable);
    }
}
