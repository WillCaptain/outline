package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.Token;
import org.twelve.gcp.node.expression.SymbolIdentifier;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

public class SymbolIndentifierConverter extends Converter {
    public SymbolIndentifierConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return new SymbolIdentifier(ast,new Token<>(source.lexeme()));
    }
}
