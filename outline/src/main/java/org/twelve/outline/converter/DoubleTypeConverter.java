package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.typeable.IdentifierTypeNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;

import static org.twelve.outline.common.Tool.convertStrToken;

public class DoubleTypeConverter implements Converter{
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return new IdentifierTypeNode(new Identifier(ast, convertStrToken(((TerminalNode) source).token())));
    }
}
