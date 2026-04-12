package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.typeable.NullableTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

/**
 * Wraps the already-converted inner {@link TypeNode} (passed in as {@code related}) into
 * a {@link NullableTypeNode}.  This converter is invoked from {@link FactorTypeConverter}
 * when a trailing {@code nullable_suffix} ('?') is present in the parse tree.
 */
public class NullableTypeConverter extends Converter {
    public NullableTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return new NullableTypeNode(cast(related));
    }
}
