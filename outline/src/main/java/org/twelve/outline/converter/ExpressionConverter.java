package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.As;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.IsAs;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ExpressionConverter extends Converter {
    public ExpressionConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode firstChild = ((NonTerminalNode) source).node(0);
        if (Constants.FUNCTION.equals(firstChild.name())) {
            return converters.get(Constants.FUNCTION).convert(ast, firstChild);
        }
        NonTerminalNode is_as = cast(((NonTerminalNode) source).node(1));
        Expression convertedExpression = cast(converters.get(firstChild.name()).convert(ast, firstChild));
        if(is_as.name().equals(Constants.AS_EXPRESSION)) {
            return parseAsExpression(ast, is_as, convertedExpression);
        }else{//is expression
            return parseIsAsExpression(ast,is_as,convertedExpression);
        }
    }

    private As parseAsExpression(AST ast, NonTerminalNode is_as, Expression expression) {
        ParseNode type = is_as.node(1);
        TypeNode convertedType = cast(converters.get(Constants.COLON_ + type.name()).convert(ast, type));
        return new As(expression, convertedType);
    }
    private IsAs parseIsAsExpression(AST ast, NonTerminalNode is_as, Expression expression) {
        ParseNode type = is_as.node(1);
        TypeNode convertedType = cast(converters.get(Constants.COLON_ + type.name()).convert(ast, type));
        if(is_as.nodes().size()>2){
            ParseNode as = is_as.node(3);
            Expression convertedAs = cast(converters.get(as.name()).convert(ast,as));
            return new IsAs(expression,convertedType,cast(convertedAs));
        }else {
            // Keep original narrowing behavior for identifiers (`x is T`) by
            // preserving the implicit bind target = lhs identifier.
            if (expression instanceof Identifier id) {
                return new IsAs(id, convertedType);
            }
            // For non-identifier lhs (e.g. member access), there is no implicit bind target.
            return new IsAs(expression, convertedType);
        }
    }
}
