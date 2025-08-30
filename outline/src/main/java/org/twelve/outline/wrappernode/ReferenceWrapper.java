package org.twelve.outline.wrappernode;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.typeable.TypeNode;

public class ReferenceWrapper extends ArgumentWrapper {
    public ReferenceWrapper(AST ast, Identifier arg, TypeNode typeNode) {
        super(ast,arg,typeNode);
    }
}
