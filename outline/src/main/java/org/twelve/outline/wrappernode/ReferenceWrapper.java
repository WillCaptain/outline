package org.twelve.outline.wrappernode;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.AbstractNode;
import org.twelve.gcp.ast.Node;

import java.util.List;

public class ReferenceWrapper extends AbstractNode {
    private final List<Node> refs;

    public ReferenceWrapper(AST ast, List<Node> refs) {
        super(ast);
        this.refs = refs;
    }

    public List<Node> refs() {
        return this.refs;
    }
}
