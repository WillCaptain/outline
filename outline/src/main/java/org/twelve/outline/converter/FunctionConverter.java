package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.expression.Expression;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.expression.PolyNode;
import org.twelve.gcp.node.expression.body.Body;
import org.twelve.gcp.node.expression.body.FunctionBody;
import org.twelve.gcp.node.expression.referable.ReferenceNode;
import org.twelve.gcp.node.expression.typeable.TypeNode;
import org.twelve.gcp.node.function.Argument;
import org.twelve.gcp.node.function.FunctionNode;
import org.twelve.gcp.node.statement.ReturnStatement;
import org.twelve.msll.parsetree.NonTerminalNode;
import org.twelve.msll.parsetree.ParseNode;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;
import org.twelve.outline.wrappernode.ArgumentWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.twelve.outline.common.Tool.cast;

public class FunctionConverter extends Converter {
    public FunctionConverter(Map<String, Converter> converters) {
        super(converters);
    }

    @Override
    public Node convert(AST ast, ParseNode source, Node related) {
        List<ParseNode> nodes = ((NonTerminalNode)source).nodes();
        List<FunctionNode> functions = new ArrayList<>();

        while(!nodes.isEmpty()) {
            List<ReferenceNode> refs = new ArrayList<>();
            List<Argument> args = new ArrayList<>();
            if (nodes.getFirst().name().equals(Constants.REFERENCE_TYPE)) {
                convertArgOrRef(ast, cast(nodes.removeFirst()), refs);
            }
            ParseNode lambdaArgsNode = nodes.removeFirst();
            TypeNode declaredReturn = extractDeclaredReturn(ast, lambdaArgsNode);
            convertArgOrRef(ast, cast(lambdaArgsNode), args);

            nodes.removeFirst();
            ParseNode originBody = nodes.removeFirst();
            Node statements = converters.get(originBody.name()).convert(ast, originBody);
            FunctionBody body = new FunctionBody(ast);
            if (statements instanceof Body) {
                for (Node node : statements.nodes()) {
                    body.addStatement(cast(node));
                }
            } else {
                body.addStatement(new ReturnStatement((Expression) statements));
            }

            functions.add(FunctionNode.from(body, refs, args, declaredReturn));

            if(!nodes.isEmpty()){
                nodes = ((NonTerminalNode)nodes.get(1)).nodes();
            }
        }
        if(functions.size()==1){
            return functions.getFirst();
        }else{
            return new PolyNode(functions.removeFirst(),functions.toArray(new FunctionNode[0]));
        }
    }
    /**
     * The msll parse tree flattens the {@code function_args'} sub-production
     * into its parent {@code lambda_args}, so trailing {@code ')' ':' type}
     * is the signal for a lambda return-type annotation. Peels it and converts
     * to a {@link TypeNode}; returns null when absent. The input parse node is
     * left intact — {@link #convertArgOrRef} strips the same tail when building
     * the argument list so both sides stay consistent.
     */
    /** package-private so {@link FnDeclaratorConverter} can reuse the same peel logic. */
    TypeNode extractDeclaredReturn(AST ast, ParseNode lambdaArgs) {
        if (!(lambdaArgs instanceof NonTerminalNode nt)) return null;
        List<ParseNode> children = nt.nodes();
        int n = children.size();
        if (n < 3) return null;
        ParseNode maybeColon = children.get(n - 2);
        ParseNode maybeRParen = children.get(n - 3);
        if (!Constants.COLON_.equals(maybeColon.lexeme())) return null;
        if (!")".equals(maybeRParen.lexeme())) return null;
        ParseNode typeSrc = children.get(n - 1);
        if ("non_func_type".equals(typeSrc.name()) && typeSrc instanceof NonTerminalNode nt2) {
            typeSrc = nt2.nodes().get(0);
        }
        Converter cvt = converters.get(Constants.COLON_ + typeSrc.name());
        if (cvt == null) return null;
        Node converted = cvt.convert(ast, typeSrc);
        return converted instanceof TypeNode tn ? tn : null;
    }

    /** package-private so {@link FnDeclaratorConverter} can reuse the same arg/ref walker. */
    void convertArgOrRef(AST ast,ParseNode parent,List list){
        List<ParseNode> args = new ArrayList<>();
        if(parent instanceof TerminalNode){
            args.add(parent);
        }else{
            List<ParseNode> children = ((NonTerminalNode)parent).nodes();
            // Layouts to recognise:
            //   a. ID                                 — untyped single-ID (size 1)
            //   b. ID ':' non_func_type               — typed single-ID (size 3, first=TerminalNode)
            //   c. function_args'                     — parenthesised args only (size 1)
            //   d. function_args' ':' non_func_type   — parens + declared return (size 3,
            //                                            first=NonTerminalNode). The trailing
            //                                            `:R` has been peeled off by
            //                                            extractDeclaredReturn, so recurse into
            //                                            the inner function_args' only.
            boolean isTypedSingle = children.size() == 3
                    && children.get(0) instanceof TerminalNode
                    && Constants.COLON_.equals(children.get(1).lexeme());
            // msll flattens function_args' into lambda_args. Detect `... ')' ':' type`
            // trailing return-type annotation and strip those 3 tokens here — the
            // type is handled out-of-band by extractDeclaredReturn.
            int n2 = children.size();
            boolean hasTrailingReturn = n2 >= 3
                    && ")".equals(children.get(n2 - 3).lexeme())
                    && Constants.COLON_.equals(children.get(n2 - 2).lexeme());
            if (isTypedSingle) {
                args.add(parent);
            } else if (hasTrailingReturn) {
                args.addAll(children.subList(0, n2 - 2));
            } else {
                args.addAll(children);
            }
        }
        args.stream().filter(n -> !"<(,)>".contains(n.lexeme())).forEach(n -> {
            if(n.name().equals(Constants.FUNC_HEAD)) return;
            Identifier arg;
            TypeNode typeNode = null;
            // Reshaped typed lambda_args (ID ':' non_func_type) has the same
            // child layout as `argument`, so reuse ArgumentConverter directly.
            String converterKey = (n.name().equals("lambda_args")
                    && n instanceof NonTerminalNode
                    && ((NonTerminalNode) n).nodes().size() == 3)
                    ? Constants.ARGUMENT
                    : n.name();
            Node converted = converters.get(converterKey).convert(ast, n);
            if(converted instanceof ArgumentWrapper){
                arg = ((ArgumentWrapper) converted).argument();
                typeNode = ((ArgumentWrapper) converted).typeNode();
            }else{
                arg = cast(converted);
            }
            if(parent.name().equals(Constants.REFERENCE_TYPE)){
                list.add(new ReferenceNode(arg, typeNode));
            }else {
                list.add(new Argument(arg, typeNode));
            }
        });
    }
}
