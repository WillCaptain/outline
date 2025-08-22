package org.twelve.outline.converters;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.base.Program;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.twelve.outline.common.Tool.cast;
import static org.twelve.outline.common.Tool.convertStrToken;

public class ModuleConverter implements  Converter {
    @Override
    public Node convert(AST ast, ParseNode source, Node targetParent) {
        NonTerminalNode module = cast(((NonTerminalNode) source).node(1));
        Program program = cast(targetParent.parent());
        List<Identifier> ns = new ArrayList<>(module.nodes().stream().filter(n -> !n.name().equals(Constants.DOT))
                .map(n -> new Identifier(ast, convertStrToken(((TerminalNode) n).token())))
                .collect(Collectors.toUnmodifiableList()));
        program.setNamespace(ns);
        return program;
    }
}
