package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.common.Pair;
import org.twelve.gcp.node.expression.DictNode;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class MapNodeConverter extends Converter {
    public MapNodeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode map = cast(source);
        if (map.nodes().size() == 3) {//empty map
            return new DictNode(ast);
        }
        int i = 1;
        List<Pair<Expression, Expression>> tuples = new ArrayList<>();
        while (i < map.nodes().size()) {
            Expression key = cast(converters.get(map.node(i).name()).convert(ast, map.node(i)));
            i += 2;
            Expression value = cast(converters.get(map.node(i).name()).convert(ast, map.node(i)));
            i += 2;
            tuples.add(new Pair<>(key,value));
        }
        return new DictNode(ast,tuples.toArray(new Pair[0]));
    }
}
