package org.twelve.outline;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.typeable.TupleTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.converter.Converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class TupleTypeConverter implements Converter {
    private final Map<String, Converter> converters;

    public TupleTypeConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<TypeNode> types = new ArrayList<>();
        ((NonTerminalNode)source).nodes().stream().filter(n->!"(,)".contains(n.lexeme())).forEach(n->{
            types.add(cast(converters.get(Constants.COLON_+n.name()).convert(ast,n,null)));
        });
        return new TupleTypeNode(types);
    }
}
