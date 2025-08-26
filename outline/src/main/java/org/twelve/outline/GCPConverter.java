package org.twelve.outline;

import org.twelve.outline.converters.*;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.msll.parsetree.ParserTree;
import org.twelve.msll.parsetree.TerminalNode;
import org.twelve.outline.common.Constants;

import java.util.HashMap;
import java.util.Map;

import static org.twelve.outline.common.Tool.*;


public class GCPConverter {
    private final ASF asf;
    private Map<String, Converter> converters = new HashMap<>();

    public GCPConverter(ASF asf) {
        this.asf = asf;
        //root, program
        this.converters.put(Constants.ROOT, (ast, source, targetParent) ->
                new ProgramConverter(converters).convert(ast, source, targetParent));
        //module
        this.converters.put(Constants.MODULE_STATEMENT, (ast, source, targetParent) ->
                new ModuleConverter(converters).convert(ast, source, targetParent));
        //import
        this.converters.put(Constants.IMPORT_STATEMENT, (ast, source, targetParent) ->
                new ImportConverter(converters).convert(ast, source, targetParent));
        //export
        this.converters.put(Constants.EXPORT_STATEMENT, (ast, source, targetParent) ->
                new ExportConverter().convert(ast, source, targetParent));
        //variable declarator
        this.converters.put(Constants.VARIABLE_DECLARATOR,
                (ast, source, targetParent) ->
                        new VarDeclareConverter(converters).convert(ast, source, targetParent));
        //type
        this.converters.put(Constants.ID_TYPE, (ast, source, targetParent) ->
                new IDTypeConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.INT_TYPE, (ast, source, targetParent) ->
            new IntTypeConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.DOUBLE_TYPE, (ast, source, targetParent) ->
                new DoubleTypeConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.TUPLE_TYPE, (ast, source, targetParent) ->
                new TupleTypeConverter(converters).convert(ast,source,targetParent));
        this.converters.put(Constants.QUESTION_TYPE, (ast, source, targetParent) ->
                new QuestionTypeConverter().convert(ast,source,targetParent));
        //literal
        this.converters.put(Constants.STRING, (ast, source, targetParent) ->
                new StringLiteralConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.DOUBLE, (ast, source, targetParent) ->
                new NumberLiteralConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.NUMBER, (ast, source, targetParent) ->
                new NumberLiteralConverter().convert(ast,source,targetParent));
        //id
        this.converters.put(Constants.ID, (ast, source, targetParent) ->
                new Identifier(ast, convertStrToken(((TerminalNode) source).token())));
        //function
        this.converters.put(Constants.FUNCTION, (ast, source, targetParent) ->
                new FunctionConverter(converters).convert(ast, source, targetParent));
        //numeric expression
        this.converters.put(Constants.NUMERIC_EXPRESSION, (ast, source, targetParent) ->
                new NumericExprConverter(converters).convert(ast, source, targetParent));
        //body
        this.converters.put(Constants.BLOCK, (ast, source, targetParent) ->
                new BodyConverter(converters).convert(ast, source, targetParent));
        //entity
        this.converters.put(Constants.ENTITY, (ast, source, targetParent) ->
                new EntityConverter(converters).convert(ast, source, targetParent));
        //entity property assignment
        this.converters.put(Constants.PROPERTY_ASSIGNMENT, (ast, source, targetParent) ->
                new PropertyAssignmentConverter(converters).convert(ast, source, targetParent));
        //tuple
        this.converters.put(Constants.TUPLE, (ast, source, targetParent) ->
                new TupleConverter(converters).convert(ast, source, targetParent));
        //factor expression
        this.converters.put(Constants.FACTOR_EXPRESSION, (ast, source, targetParent) ->
                new FactorExprConverter(converters).convert(ast, source, targetParent));
        //this
        this.converters.put(Constants.THIS, (ast, source, targetParent) ->
                new ThisConverter().convert(ast, source, targetParent));
        //member accessor
        this.converters.put(Constants.MEMBER_ACCESSOR, (ast, source, targetParent) ->
                new MemberAccessorConverter(converters).convert(ast, source, targetParent));
        //function call
        this.converters.put(Constants.FUNCTION_CALL, (ast, source, targetParent) ->
                new FunctionCallConverter(converters).convert(ast, source, targetParent));
    }

    public GCPConverter() {
        this(new ASF());
    }

    public AST convert(ParserTree tree) {
        AST ast = this.asf.newAST();
        converters.get(tree.start().name()).convert(ast, tree.start(), ast.program());
        return ast;
    }


}



