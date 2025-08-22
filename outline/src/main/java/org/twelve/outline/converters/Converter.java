package org.twelve.outline.converters;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.Token;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Tool;

public interface Converter {
    Node convert(AST ast, ParseNode source, Node targetParent);

}
