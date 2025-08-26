package org.twelve.outline.converters;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.base.Program;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ProgramConverter implements Converter{
    private final Map<String, Converter> converters;

    public ProgramConverter(Map<String, Converter> converters){
        this.converters = converters;
    }
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode root = cast(source);
        Program program = cast(related);
        for (ParseNode node : root.nodes()) {
            converters.get(node.name()).convert(ast, node, program.body());
        }
        return program;
    }
}
