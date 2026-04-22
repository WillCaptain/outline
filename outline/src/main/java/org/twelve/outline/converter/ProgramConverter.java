package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.base.Program;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.statement.ReturnStatement;
import org.twelve.gcp.node.statement.Statement;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class ProgramConverter extends Converter{
    public ProgramConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode root = cast(source);
        Program program = cast(related);
        for (ParseNode node : root.nodes()) {
            // Resilient parsing (panic-mode recovery in MSLL) can attach extra
            // "recovery" subtrees to the root whose grammar name has no registered
            // converter (e.g. an empty/partial "statement" stub). Skip those so a
            // single malformed statement does not poison conversion of the rest.
            Converter cvt = converters.get(node.name());
            if (cvt == null) continue;
            Node converted;
            try {
                converted = cvt.convert(ast, node, program.body());
            } catch (RuntimeException ex) {
                // Per-statement conversion failure must not abort the whole module:
                // downstream tooling (IDE markers) surfaces the syntax errors
                // collected by the parser via AST#syntaxErrors().
                continue;
            }
            if (converted instanceof Expression) {
                program.body().addStatement(new ReturnStatement((Expression) converted));
            }
        }
        return program;
    }
}
