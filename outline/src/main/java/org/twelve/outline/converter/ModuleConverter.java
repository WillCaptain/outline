package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.base.Program;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.accessor.MemberAccessor;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ModuleConverter extends   Converter {
    public ModuleConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        ParseNode module = ((NonTerminalNode) source).node(1);
        Program program = cast(related.parent());
        Node ns = converters.get(module.name()).convert(ast,module);
        if(ns instanceof Identifier) {
            program.setNamespace((Identifier) ns);
        }else{
            program.setNamespace((MemberAccessor) ns);
        }
        return program;
    }
}
