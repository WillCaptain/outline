package org.twelve.outline.wrappernode;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.AbstractNode;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.expression.typeable.TypeNode;

public class ArgumentWrapper extends AbstractNode {
    private final Identifier argument;
    private final TypeNode typeNode;
    private final boolean mutable;

    public ArgumentWrapper(AST ast, Identifier arg, TypeNode typeNode) {
        this(ast, arg, typeNode, false);
    }

    public ArgumentWrapper(AST ast, Identifier arg, TypeNode typeNode, boolean mutable) {
        super(ast);
        this.argument = arg;
        this.typeNode = typeNode;
        this.mutable = mutable;
    }

    public Identifier argument(){
        return this.argument;
    }

    public TypeNode typeNode(){
        return this.typeNode;
    }

    public boolean mutable(){
        return this.mutable;
    }
}
