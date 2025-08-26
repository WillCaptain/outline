package org.twelve.outline.converters;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.common.Pair;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.imexport.Export;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;

import java.util.ArrayList;
import java.util.List;

import static org.twelve.outline.common.Tool.cast;
import static org.twelve.outline.common.Tool.convertStrToken;

public class ExportConverter implements Converter{
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode exports = cast(source);
        List<Pair<Identifier, Identifier>> vars = new ArrayList<>();
        Identifier key = null, value = null;
        int i = 0;
        while (i < exports.nodes().size()) {
            if (exports.node(i).name().equals(Constants.EXPORT)) {
                i++;
                key = new Identifier(ast, convertStrToken(((TerminalNode) exports.node(i)).token()));
                i++;
                continue;
            }
            if (exports.node(i).name().equals(Constants.AS)) {
                i++;
                value = new Identifier(ast, convertStrToken(((TerminalNode) exports.node(i)).token()));
                i++;
                continue;
            }
            if (exports.node(i).name().equals(Constants.COMMA)) {
                vars.add(new Pair<>(key, value));
                i++;
                key = new Identifier(ast, convertStrToken(((TerminalNode) exports.node(i)).token()));
                value = null;
                i++;
            }
            if (exports.node(i).name().equals(Constants.SEMICOLON)) {
                vars.add(new Pair<>(key, value));
                break;
            }
        }
        return ast.addExport(new Export(vars));
    }
}
