package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.common.Pair;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.accessor.MemberAccessor;
import org.twelve.gcp.node.imexport.Import;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;
import static org.twelve.outline.common.Tool.convertStrToken;

public class ImportConverter implements Converter{
    private final Map<String, Converter> converters;

    public ImportConverter (Map<String, Converter> converters){
        this.converters = converters;
    }
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode imports = cast(source);
        List<Pair<Identifier, Identifier>> vars = new ArrayList<>();
        Identifier key = null, value = null;
        List<Identifier> froms = new ArrayList<>();
        int i = 0;
        while (i < imports.nodes().size()) {
            if (imports.node(i).name().equals(Constants.IMPORT)) {
                i++;
                key = new Identifier(ast, convertStrToken(((TerminalNode) imports.node(i)).token()));
                i++;
                continue;
            }
            if (imports.node(i).name().equals(Constants.AS)) {
                i++;
                value = new Identifier(ast, convertStrToken(((TerminalNode) imports.node(i)).token()));
                i++;
                continue;
            }
            if (imports.node(i).name().equals(Constants.COMMA)) {
                vars.add(new Pair<>(key, value));
                i++;
                key = new Identifier(ast, convertStrToken(((TerminalNode) imports.node(i)).token()));
                value = null;
                i++;
            }
            if (imports.node(i).name().equals(Constants.FROM)) {
                vars.add(new Pair<>(key, value));
                Node from = converters.get(imports.node(i + 1).name()).convert(ast,imports.node(i + 1),null);
               while(from instanceof MemberAccessor){
                   froms.add(0,((MemberAccessor) from).member());
                   from = ((MemberAccessor) from).host();
               }
               froms.add(0,cast(from));
                break;
            }
        }
        return ast.addImport(new Import(vars, froms));
    }
}
