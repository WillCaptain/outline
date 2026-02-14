package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.typeable.LiteralTypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class LiteralTypeConverter extends Converter {
    public LiteralTypeConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode node = source;
        if(source.name().equals(Constants.LITERAL_TYPE_)){
            node = ((NonTerminalNode) source).node(1);
        }
        return new LiteralTypeNode(cast(converters.get(node.name()).convert(ast, node)));
//        if(source instanceof TerminalNode) {
//            return new LiteralTypeNode(cast(converters.get(source.name()).convert(ast, source)));
//        }else{
//            ParseNode literal = ((NonTerminalNode)source).node(1);
//            return new LiteralTypeNode(cast(converters.get(literal.name()).convert(ast, literal)));
//        }
    }
}
