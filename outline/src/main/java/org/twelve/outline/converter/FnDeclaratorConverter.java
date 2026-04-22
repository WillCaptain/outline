package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.common.VariableKind;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.body.Body;
import org.twelve.gcp.node.expression.body.FunctionBody;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.gcp.node.function.Argument;
import org.twelve.gcp.node.function.FunctionNode;
import org.twelve.gcp.node.statement.ReturnStatement;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.wrappernode.ArgumentWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;
import static org.twelve.outline.common.Tool.convertStrToken;

/**
 * Converts the {@code fn name<generics>(args):R { body }} statement into its
 * {@code let name = <generics>(args):R -> { body };} desugaring. {@code fn}
 * is pure syntactic sugar — it builds the same
 * {@link VariableDeclarator} + {@link FunctionNode} pair that the {@code let
 * } + lambda form produces, so every downstream tool (inference, hover,
 * interpreter) sees a single canonical shape.
 *
 * <p>The {@code :R} annotation is optional and, when present, is attached
 * to the innermost {@link FunctionNode} via
 * {@link FunctionNode#from(FunctionBody, List, List, TypeNode)} — the same
 * path used by the lambda converter.
 *
 * <p>Recursion works transparently through the existing multi-round
 * inference + {@code IdentifierInference.confirmRecursive} path, because
 * the desugared form is an ordinary {@code let}-bound function.
 */
public class FnDeclaratorConverter extends Converter {
    private final FunctionConverter functionConverter;

    public FnDeclaratorConverter(Map<String, Converter> converters,
                                 FunctionConverter functionConverter) {
        super(converters);
        this.functionConverter = functionConverter;
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        NonTerminalNode fnNode = cast(source);
        List<ParseNode> children = fnNode.nodes();
        int i = 0;
        if (i < children.size() && Constants.FUNC_HEAD.equals(children.get(i).name())) i++;
        TerminalNode idNode = cast(children.get(i++));
        Identifier nameId = new Identifier(ast, convertStrToken(idNode.token()));

        List<ReferenceNode> refs = new ArrayList<>();
        if (i < children.size()
                && Constants.REFERENCE_TYPE.equals(children.get(i).name())) {
            functionConverter.convertArgOrRef(ast, children.get(i), refs);
            i++;
        }

        List<Argument> args = new ArrayList<>();
        ParseNode argsNode = children.get(i);
        if (argsNode instanceof NonTerminalNode) {
            // function_args' preserved as a sub-tree (non-flattened case)
            functionConverter.convertArgOrRef(ast, argsNode, args);
            i++;
        } else {
            // msll may flatten `function_args'` into `function_declarator`; walk
            // siblings from `(` through the matching `)` and convert each argument.
            int start = i;
            while (i < children.size() && !")".equals(children.get(i).lexeme())) i++;
            int end = i;
            for (int k = start + 1; k < end; k++) {
                ParseNode c = children.get(k);
                if ("<(,)>".contains(c.lexeme())) continue;
                Converter argCvt = converters.get(c.name());
                Node converted = argCvt.convert(ast, c);
                Identifier argId;
                TypeNode argTypeNode = null;
                if (converted instanceof ArgumentWrapper aw) {
                    argId = aw.argument();
                    argTypeNode = aw.typeNode();
                } else {
                    argId = cast(converted);
                }
                args.add(new Argument(argId, argTypeNode));
            }
            i = end + 1;
        }

        TypeNode declaredReturn = null;
        if (i < children.size()
                && Constants.COLON_.equals(children.get(i).lexeme())) {
            i++;
            ParseNode typeSrc = children.get(i++);
            if ("non_func_type".equals(typeSrc.name())
                    && typeSrc instanceof NonTerminalNode nt2) {
                typeSrc = nt2.nodes().get(0);
            }
            Converter cvt = converters.get(Constants.COLON_ + typeSrc.name());
            if (cvt != null) {
                Node converted = cvt.convert(ast, typeSrc);
                if (converted instanceof TypeNode tn) declaredReturn = tn;
            }
        }

        ParseNode blockNode = children.get(i);
        Node statements = converters.get(blockNode.name()).convert(ast, blockNode);
        FunctionBody body = new FunctionBody(ast);
        if (statements instanceof Body) {
            for (Node s : statements.nodes()) body.addStatement(cast(s));
        } else {
            body.addStatement(new ReturnStatement((Expression) statements));
        }

        FunctionNode fn = FunctionNode.from(body, refs, args, declaredReturn);
        VariableDeclarator declarator = new VariableDeclarator(ast, VariableKind.LET);
        declarator.declare(nameId, null, fn);
        if (related != null) ((Body) related).addStatement(declarator);
        return declarator;
    }
}
