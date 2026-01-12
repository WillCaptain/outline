package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.unpack.TupleUnpackNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TupleUnpackNodeConverter extends Converter {
    public TupleUnpackNodeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<Node> begins = new ArrayList<>();
        List<Node> ends = new ArrayList<>();
        List<Node> nodes = begins;
        for (ParseNode node : ((NonTerminalNode) source).nodes()) {
            if(node.name().equals(Constants.ID)||node.name().equals(Constants.UNDER_LINE)||
            node.name().equals(Constants.TUPLE_UNPACK)||node.name().equals(Constants.ENTITY_UNPACK)){
                nodes.add(converters.get(node.name()).convert(ast,node));
            }
            if(node.name().contains("_Type")){
                nodes.add(converters.get(Constants.COLON_+node.name()).convert(ast,node));
            }
            if (node.lexeme().equals(Constants.DOT_DOT_DOT_)) nodes = ends;
        }
        return new TupleUnpackNode(ast, begins, ends);
    }
}
