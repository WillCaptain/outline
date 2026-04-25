package org.twelve.outline;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.EntityTypeNode;
import org.twelve.gcp.node.expression.typeable.ExtendTypeNode;
import org.twelve.gcp.node.expression.typeable.ReferenceAliasTypeNode;
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
            if (!nodes.isEmpty() && "(".equals(nodes.getFirst().lexeme())) {
                nodes.removeFirst(); // (
                ParseNode adtNode = nodes.removeFirst();
                TypeNode body = cast(converters.get(Constants.COLON_ + adtNode.name()).convert(ast, adtNode));
                return new ReferenceAliasTypeNode(ast, refs, body);
            }
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

        // ── Alt 2b: single-constructor variant like `Male(String,Int)` ─────────
        if (!nodes.isEmpty() && nodes.getFirst().name().equals("symbol_tuple_variant")) {
            ParseNode node = nodes.removeFirst();
            return converters.get(Constants.COLON_ + node.name()).convert(ast, node);
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

        ParseNode extNode = nodes.getFirst();
        // MSLL grammar ambiguity: when a lambda '->' inside the entity body is misinterpreted
        // as func_type's '->', a spurious 'non_compound_type' sibling may appear after the
        // real entity_type.  Skip it – the entity_type already captured the full body.
        if (!extNode.name().equals(Constants.ENTITY_TYPE.substring(1))) {
            return base;
        }
        nodes.removeFirst();
        EntityTypeNode extension = cast(converters.get(Constants.COLON_ + extNode.name())
                .convert(ast, extNode));
        return new ExtendTypeNode(ast, new ArrayList<>(), base, null, extension);
    }
}
