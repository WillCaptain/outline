package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.Token;
import org.twelve.gcp.node.expression.UnderLineNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

public class UnderlineConverter extends Converter {
    public UnderlineConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return new UnderLineNode(ast,new Token<>(source.lexeme(),source.location().start()));
    }
}
