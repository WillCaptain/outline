package org.twelve.outline.converters;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.base.Program;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.accessor.MemberAccessor;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.twelve.outline.common.Tool.cast;
import static org.twelve.outline.common.Tool.convertStrToken;

public class ModuleConverter implements  Converter {
    private final Map<String, Converter> converters;

    public ModuleConverter(Map<String, Converter> converters) {
        this.converters = converters;
    }
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode module = ((NonTerminalNode) source).node(1);
        Program program = cast(related.parent());
        Node ns = converters.get(module.name()).convert(ast,module,null);
//        if(module instanceof NonTerminalNode){
//            ns = converters.get(((NonTerminalNode) module).explain()).convert(ast,module,null);
//        }else{
//            ns = converters.get(module.name()).convert(ast,module,null);
//        }
//        List<Identifier> ns = new ArrayList<>(module.nodes().stream().filter(n -> !n.name().equals(Constants.DOT))
//                .map(n -> new Identifier(ast, convertStrToken(((TerminalNode) n).token())))
//                .collect(Collectors.toUnmodifiableList()));
        if(ns instanceof Identifier) {
            program.setNamespace((Identifier) ns);
        }else{
            program.setNamespace((MemberAccessor) ns);
        }
        return program;
    }
}
