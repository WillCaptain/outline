package org.twelve.outline.wrappernode;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.AbstractNode;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.typeable.TypeNode;

public class ArgumentWrapper extends AbstractNode {
    private final Identifier argument;
    private final TypeNode typeNode;

    public ArgumentWrapper(AST ast, Identifier arg, TypeNode typeNode) {
        super(ast);
        this.argument = arg;
        this.typeNode = typeNode;
    }

    public Identifier argument(){
        return this.argument;
    }

    public TypeNode typeNode(){
        return this.typeNode;
    }
}
