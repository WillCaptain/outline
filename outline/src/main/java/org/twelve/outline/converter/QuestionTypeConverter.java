package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.typeable.Question;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Tool;

public class QuestionTypeConverter implements Converter {
    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        return new Question(ast, Tool.convertStrToken(((TerminalNode) source).token()));
    }
}
