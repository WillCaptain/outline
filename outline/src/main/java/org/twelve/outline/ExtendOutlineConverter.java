package org.twelve.outline;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.EntityTypeNode;
import org.twelve.gcp.node.expression.typeable.ExtendTypeNode;
import org.twelve.gcp.node.expression.typeable.ReferenceCallTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.gcp.outline.projectable.Reference;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.common.Tool;
import org.twelve.outline.converter.Converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ExtendOutlineConverter extends Converter {
    public ExtendOutlineConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<ParseNode> nodes = new ArrayList<>(((NonTerminalNode) source).nodes());

        // ── Alt 3: reference_type Symbol reference_call? entity_type? ──────────
        if (!nodes.isEmpty() && nodes.getFirst().name().equals(Constants.REFERENCE_TYPE)) {
            List<ReferenceNode> refs = Tool.convertReferences(converters, cast(nodes.removeFirst()), ast);
            ParseNode symbolNode = nodes.removeFirst();   // Symbol
            TypeNode base = cast(converters.get(Constants.COLON_ + symbolNode.name()).convert(ast, symbolNode));
            ReferenceCallTypeNode refArgs = null;
            if (!nodes.isEmpty() && nodes.getFirst().name().equals(Constants.REFERENCE_CALL)) {
                refArgs = cast(converters.get(Constants.COLON_ + nodes.getFirst().name())
                        .convert(ast, nodes.removeFirst(), base));
            }
            if (nodes.isEmpty()) {
                return base;
            }
            EntityTypeNode extension = cast(converters.get(Constants.COLON_ + nodes.getFirst().name())
                    .convert(ast, nodes.removeFirst()));
            return new ExtendTypeNode(ast, refs, base, refArgs, extension);
        }

        // ── Alt 2: adt_type alone (no entity extension) ─────────────────────────
        if (!nodes.isEmpty() && nodes.getFirst().name().equals("adt_type")) {
            ParseNode adtNode = nodes.removeFirst();
            return converters.get(Constants.COLON_ + adtNode.name()).convert(ast, adtNode);
        }

        // ── Alt 1: non_compound_type entity_type? ───────────────────────────────
        // Unwrap non_compound_type (which is func_type or factor_type)
        ParseNode typeNode = nodes.removeFirst();
        if (typeNode.name().equals("non_compound_type")) {
            // Unwrap the single child inside non_compound_type
            typeNode = ((NonTerminalNode) typeNode).nodes().getFirst();
        }
        TypeNode base = cast(converters.get(Constants.COLON_ + typeNode.name()).convert(ast, typeNode));

        if (nodes.isEmpty()) {
            return base;
        }
        EntityTypeNode extension = cast(converters.get(Constants.COLON_ + nodes.getFirst().name())
                .convert(ast, nodes.removeFirst()));
        return new ExtendTypeNode(ast, new ArrayList<>(), base, null, extension);
    }
}
