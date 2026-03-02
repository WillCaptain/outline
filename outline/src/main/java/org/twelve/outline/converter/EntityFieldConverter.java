package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.expression.typeable.ArrayTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.wrappernode.ArgumentWrapper;
import org.twelve.outline.wrappernode.EntityFieldWithDefaultWrapper;

import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

/**
 * Converts an {@code entity_field} parse node into either:
 * <ul>
 *   <li>{@link ArgumentWrapper}               – for {@code fieldName: TypeName} (plain type annotation)</li>
 *   <li>{@link EntityFieldWithDefaultWrapper} – for {@code fieldName: "value"} (default-value shorthand)</li>
 * </ul>
 */
public class EntityFieldConverter extends Converter {

    public EntityFieldConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode field = cast(source);
        Identifier identifier = cast(converters.get(field.node(0).name()).convert(ast, field.node(0)));

        if (field.nodes().size() < 3) {
            // bare ID – no type annotation, no default
            return new ArgumentWrapper(ast, identifier, null);
        }

        ParseNode typeOrDefault = field.node(2);
        String typeName = typeOrDefault.name();

        // entity_field: ID ':' literal  →  default-value field (e.g. alias: "alice")
        // The MSLL parser may either wrap the literal in a 'literal' non-terminal, or
        // collapse it directly to the terminal token (STRING, INT, FLOAT, DOUBLE, number).
        if (Constants.LITERAL.equals(typeName)) {
            Node defaultNode = converters.get(Constants.LITERAL).convert(ast, typeOrDefault);
            return new EntityFieldWithDefaultWrapper(ast, identifier, defaultNode);
        }
        // MSLL may collapse  literal: <child>  →  <child>  for any single-production path.
        // Handle the most common collapsed cases: primitives, lambdas, entity/tuple literals.
        if (Constants.STRING.equals(typeName) || Constants.INT.equals(typeName)
                || Constants.LONG_LIT.equals(typeName)
                || Constants.FLOAT.equals(typeName) || Constants.DOUBLE.equals(typeName)
                || Constants.NUMBER.equals(typeName)
                || Constants.FUNCTION.equals(typeName)   // lambda collapsed
                || Constants.ENTITY.equals(typeName)     // entity literal collapsed
                || Constants.TUPLE.equals(typeName)      // tuple literal collapsed
                || Constants.SYMBOL.equals(typeName)) {  // symbol literal collapsed (e.g. alias: Male)
            Node defaultNode = converters.get(typeName).convert(ast, typeOrDefault);
            return new EntityFieldWithDefaultWrapper(ast, identifier, defaultNode);
        }

        // MSLL may parse array-type annotations ([T]) as an 'array' expression node
        // instead of 'array_type'. The collapsed structure is: '[' ID ']' (3 nodes).
        // Extract the element node and build an ArrayTypeNode directly.
        if (Constants.ARRAY.equals(typeName)) {
            NonTerminalNode arr = (NonTerminalNode) typeOrDefault;
            List<ParseNode> arrNodes = arr.nodes();
            if (arrNodes.size() == 3) {
                ParseNode elemNode = arrNodes.get(1);
                TypeNode elemType = cast(converters.get(Constants.COLON_ + elemNode.name()).convert(ast, elemNode));
                return new ArgumentWrapper(ast, identifier, new ArrayTypeNode(ast, elemType));
            }
            return new ArgumentWrapper(ast, identifier, new ArrayTypeNode(ast));
        }

        // entity_field: ID ':' declared_outline  →  plain type annotation
        TypeNode typeNode = cast(
            converters.get(Constants.COLON_ + typeName).convert(ast, typeOrDefault)
        );
        return new ArgumentWrapper(ast, identifier, typeNode);
    }
}
