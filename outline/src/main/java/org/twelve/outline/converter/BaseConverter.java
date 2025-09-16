package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.BaseNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.convertStrToken;

public class BaseConverter extends Converter {
    public BaseConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return new BaseNode(ast,convertStrToken(((TerminalNode)source).token()));
    }
}
