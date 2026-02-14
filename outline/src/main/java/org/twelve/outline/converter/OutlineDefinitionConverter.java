package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.OutlineDefinition;
import org.twelve.gcp.node.expression.identifier.SymbolIdentifier;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.gcp.node.statement.OutlineDeclarator;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class OutlineDefinitionConverter extends Converter {
    public OutlineDefinitionConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode parent = cast(source);
        List<OutlineDefinition> defs = new ArrayList<>();
        int index = 1;
        while (index < parent.nodes().size()) {
            ParseNode node = parent.node(index);
            SymbolIdentifier name = cast(converters.get(node.name()).convert(ast, node));
            if(name!=null){
                index += 2;
                String typeName = Constants.COLON_ + parent.node(index).name();
                TypeNode typeNode = cast(converters.get(typeName).convert(ast, parent.node(index)));
                defs.add(new OutlineDefinition(name, typeNode));
                index += 2;
            }

        }

        return related.addNode(new OutlineDeclarator(defs));
    }
}
