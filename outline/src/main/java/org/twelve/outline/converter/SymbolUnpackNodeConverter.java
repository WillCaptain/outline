package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.unpack.EntityUnpackNode;
import org.twelve.gcp.node.unpack.SymbolEntityUnpackNode;
import org.twelve.gcp.node.unpack.SymbolTupleUnpackNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class SymbolUnpackNodeConverter extends Converter {
    public SymbolUnpackNodeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode parent = cast(source);
        TerminalNode symbol = cast(parent.node(0));
        NonTerminalNode unpack = cast(parent.node(1));
        Node symbolNode = converters.get(symbol.name()).convert(ast, symbol);
        Node unpackNode = converters.get(unpack.name()).convert(ast, unpack);
        if (unpackNode instanceof EntityUnpackNode) {
            return new SymbolEntityUnpackNode(ast, cast(symbolNode), cast(unpackNode));
        } else {
            return new SymbolTupleUnpackNode(ast, cast(symbolNode), cast(unpackNode));
        }
    }
}
