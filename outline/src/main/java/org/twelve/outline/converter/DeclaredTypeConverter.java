package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.typeable.OptionTypeNode;
import org.twelve.gcp.node.expression.typeable.PolyTypeNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.msll.exception.ParseErrCode;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.outline.common.Constants;
import org.twelve.msll.exception.ParseErrorReporter;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class DeclaredTypeConverter implements Converter {
    private final Map<String, Converter> converters;

    public DeclaredTypeConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode type = cast(source);
        boolean isOption = type.nodes().stream().anyMatch(n -> n.lexeme().equals(Constants.OR_));
        boolean isPoly = type.nodes().stream().anyMatch(n -> n.lexeme().equals(Constants.AND_));
        if (isOption && isPoly) {
            ParseErrorReporter.report(source.parseTree(), source, ParseErrCode.TYPE_DECLARED_ERROR, "either Option or Poly");
        }
        String flag = isOption ? Constants.OR_ : Constants.AND_;
        TypeNode[] types = type.nodes().stream().filter(n -> !n.lexeme().equals(flag))
                .map(n -> converters.get(Constants.COLON_+n.name()).convert(ast, n, null))
                .toArray(TypeNode[]::new);
        if(isOption) {
            return new OptionTypeNode(types);
        }else {
            return new PolyTypeNode(types);
        }
    }
}
