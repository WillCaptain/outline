package org.twelve.outline.converters;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.common.VariableKind;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.body.Body;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;

import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class VarDeclareConverter implements Converter{
    private final Map<String, Converter> converters;

    public VarDeclareConverter(Map<String, Converter> converters){
        this.converters = converters;
    }
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode varDel = cast(source);
        TerminalNode kind = cast(varDel.node(0));
        VariableDeclarator declarator = new VariableDeclarator(ast, VariableKind.valueOf(kind.name().toUpperCase()));
        Identifier var = null;
        TypeNode type = null;
        Expression value = null;
        int i = 1;
        while (i < varDel.nodes().size()) {
            if (varDel.node(i).name().equals(Constants.ID)) {
                var = cast(this.converters.get(varDel.node(i).name()).convert(ast,varDel.node(i),null));
                type = null;
                value = null;
                i++;
            }
            if (varDel.node(i).name().equals(Constants.ARGUMENT)) {
                NonTerminalNode argument = cast(varDel.node(i));
                var = cast(this.converters.get(argument.node(0).name()).convert(ast,argument.node(0),null));
                type = cast(this.converters.get(Constants.COLON_ + argument.node(2).name()).convert(ast, argument.node(2), null));
                i++;
            }
//            if (varDel.node(i).name().equals(Constants.COLON)) {
//                i++;
//                type = cast(this.converters.get(Constants.COLON_ + varDel.node(i).name()).convert(ast, varDel.node(i), null));
//                i++;
//            }
            if (varDel.node(i).name().equals(Constants.EQUAL)) {
                i++;
                value = cast(this.converters.get(varDel.node(i).name()).convert(ast, varDel.node(i), null));
                i++;
            }
            if (varDel.node(i).name().equals(Constants.COMMA)) {
                declarator.declare(var, type, value);
                i++;
            }
            if (varDel.node(i).name().equals(Constants.SEMICOLON)) {
                declarator.declare(var, type, value);
                break;
            }
        }
        if(related !=null) {
            ((Body) related).addStatement(declarator);
        }
        return declarator;
    }
}
