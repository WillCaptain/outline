package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.Token;
import org.twelve.gcp.node.expression.BoolNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

public class BoolTypeConverter extends Converter {
    public BoolTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return new BoolNode(ast,new Token<>(source.lexeme()));
    }
}
