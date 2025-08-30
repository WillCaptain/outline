package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.Token;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.typeable.IdentifierTypeNode;
import org.twelve.msll.parsetree.ParseNode;

public class IntTypeConverter implements Converter{
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        Token token = new Token("Integer", source.location().start());
        return new IdentifierTypeNode(new Identifier(ast, token));
    }
}
