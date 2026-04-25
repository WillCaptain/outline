package org.twelve.outline.wrappernode;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.AbstractNode;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.common.FieldMergeMode;
import org.twelve.gcp.node.expression.identifier.Identifier;

/**
 * Carries an entity-type field declaration whose type annotation is a literal default value,
 * e.g. {@code alias: "alice"} — meaning the field has type {@code String} with default {@code "alice"}.
 */
public class EntityFieldWithDefaultWrapper extends AbstractNode {
    private final Identifier field;
    private final Node defaultValueNode;
    private final boolean mutable;
    private final FieldMergeMode mergeMode;

    public EntityFieldWithDefaultWrapper(AST ast, Identifier field, Node defaultValueNode) {
        this(ast, field, defaultValueNode, false);
    }

    public EntityFieldWithDefaultWrapper(AST ast, Identifier field, Node defaultValueNode, boolean mutable) {
        this(ast, field, defaultValueNode, mutable, FieldMergeMode.DEFAULT);
    }

    public EntityFieldWithDefaultWrapper(AST ast, Identifier field, Node defaultValueNode, boolean mutable, FieldMergeMode mergeMode) {
        super(ast);
        this.field = field;
        this.defaultValueNode = defaultValueNode;
        this.mutable = mutable;
        this.mergeMode = mergeMode == null ? FieldMergeMode.DEFAULT : mergeMode;
    }

    public Identifier field() {
        return field;
    }

    public Node defaultValueNode() {
        return defaultValueNode;
    }

    public boolean mutable() {
        return mutable;
    }

    public FieldMergeMode mergeMode() {
        return mergeMode;
    }
}
