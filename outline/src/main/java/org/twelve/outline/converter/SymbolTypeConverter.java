package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.*;
import org.twelve.gcp.node.expression.typeable.EntityTypeNode;
import org.twelve.gcp.node.expression.typeable.TupleTypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class SymbolTypeConverter extends Converter {
    public SymbolTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        if(source instanceof TerminalNode){
            SymbolIdentifier symbol = cast(converters.get(source.name()).convert(ast, source));
            return new SymbolTupleTypeTypeNode(symbol);
        }

        NonTerminalNode parent = cast(source);
        SymbolIdentifier symbol = cast(converters.get(parent.node(0).name()).convert(ast, parent.node(0)));
        if (parent.node(1).name().equals("entity_type")) {
            EntityTypeNode entity = cast(converters.get(Constants.COLON_ + parent.node(1).name()).convert(ast, parent.node(1)));
            return new SymbolEntityTypeTypeNode(symbol, entity.members());
        }else{
            TupleTypeNode tuple = cast(converters.get(Constants.COLON_ + parent.node(1).name()).convert(ast, parent.node(1)));
            return new SymbolTupleTypeTypeNode(symbol, tuple.members().stream().map(Variable::declared).toList());
        }
    }
}
