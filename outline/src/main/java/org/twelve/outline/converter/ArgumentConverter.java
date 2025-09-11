package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.wrappernode.ArgumentWrapper;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ArgumentConverter extends Converter {
    public ArgumentConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode argument = cast(source);
        Identifier arg = cast(converters.get(argument.node(0).name()).convert(ast, argument.node(0)));
        TypeNode typeNode = cast(converters.get(Constants.COLON_+argument.node(2).name()).convert(ast, argument.node(2)));
        return new ArgumentWrapper(ast,arg,typeNode);
    }
}
