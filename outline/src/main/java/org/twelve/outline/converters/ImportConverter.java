package org.twelve.outline.converters;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.common.Pair;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.imexport.Import;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;

import java.util.ArrayList;
import java.util.List;

import static org.twelve.outline.common.Tool.cast;
import static org.twelve.outline.common.Tool.convertStrToken;

public class ImportConverter implements Converter{
    @Override
    public Node convert(AST ast, ParseNode source, Node targetParent) {
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
                ParseNode from = imports.node(i + 1);
                if(from instanceof TerminalNode) {
                    froms.add(new Identifier(ast, convertStrToken(((TerminalNode) from).token())));
                }else{
                    int j=0;
                    NonTerminalNode accessor = cast(from);
                    while(j<accessor.nodes().size()){
                        if (accessor.node(j).name().equals(Constants.ID)) {
                            froms.add(new Identifier(ast, convertStrToken(((TerminalNode) accessor.node(j)).token())));
                        }
                        j++;
                    }

                }
                break;
            }
        }
        return ast.addImport(new Import(vars, froms));
    }
}
