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
            convertArgOrRef(ast, cast(nodes.removeFirst()), args);

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

            functions.add(FunctionNode.from(body, refs, args));

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
    private void convertArgOrRef(AST ast,ParseNode parent,List list){
        List<ParseNode> args = new ArrayList<>();
        if(parent instanceof TerminalNode){
            args.add(parent);
        }else{
            List<ParseNode> children = ((NonTerminalNode)parent).nodes();
            // Untyped bare single-ID lambda_args: ID — unchanged
            // Typed bare single-ID lambda_args: ID ':' non_func_type — reshape into
            // an `argument` payload so the downstream wrapper logic can take the
            // ArgumentWrapper path uniformly with the parenthesised form.
            if (children.size() == 3 && Constants.COLON_.equals(children.get(1).lexeme())) {
                args.add(parent);
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
