package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.msll.parsetree.ParseNode;

public interface Converter {
    Node convert(AST ast, ParseNode source, Node related);

}
