package org.twelve.outline.wrappernode;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.AbstractNode;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.identifier.Identifier;

/**
 * Carries an entity-type field declaration whose type annotation is a literal default value,
 * e.g. {@code alias: "alice"} — meaning the field has type {@code String} with default {@code "alice"}.
 */
public class EntityFieldWithDefaultWrapper extends AbstractNode {
    private final Identifier field;
    private final Node defaultValueNode;

    public EntityFieldWithDefaultWrapper(AST ast, Identifier field, Node defaultValueNode) {
        super(ast);
        this.field = field;
        this.defaultValueNode = defaultValueNode;
    }

    public Identifier field() {
        return field;
    }

    public Node defaultValueNode() {
        return defaultValueNode;
    }
}
