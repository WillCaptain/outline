package org.twelve.outline.wrappernode;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.AbstractNode;
import org.twelve.gcp.common.FieldMergeMode;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.expression.typeable.TypeNode;

public class ArgumentWrapper extends AbstractNode {
    private final Identifier argument;
    private final TypeNode typeNode;
    private final boolean mutable;
    private final FieldMergeMode mergeMode;

    public ArgumentWrapper(AST ast, Identifier arg, TypeNode typeNode) {
        this(ast, arg, typeNode, false);
    }

    public ArgumentWrapper(AST ast, Identifier arg, TypeNode typeNode, boolean mutable) {
        this(ast, arg, typeNode, mutable, FieldMergeMode.DEFAULT);
    }

    public ArgumentWrapper(AST ast, Identifier arg, TypeNode typeNode, boolean mutable, FieldMergeMode mergeMode) {
        super(ast);
        this.argument = arg;
        this.typeNode = typeNode;
        this.mutable = mutable;
        this.mergeMode = mergeMode == null ? FieldMergeMode.DEFAULT : mergeMode;
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

    public FieldMergeMode mergeMode() {
        return this.mergeMode;
    }
}
