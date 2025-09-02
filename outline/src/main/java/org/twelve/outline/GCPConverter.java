package org.twelve.outline;

import org.twelve.outline.converter.*;
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
        this.converters.put(Constants.EXPRESSION, (ast, source, targetParent) ->
                new ExpressionConverter(converters).convert(ast, source, targetParent));
        //variable declarator
        this.converters.put(Constants.VARIABLE_DECLARATOR, (ast, source, targetParent) ->
                        new VarDeclareConverter(converters).convert(ast, source, targetParent));
        this.converters.put(Constants.ASSIGNMENT, (ast, source, targetParent) ->
                new AssignmentConverter(converters).convert(ast, source, targetParent));
        this.converters.put(Constants.EXPRESSION_STATEMENT, (ast, source, targetParent) ->
                new ExpresionStatementConverter(converters).convert(ast, source, targetParent));
        this.converters.put(Constants.UNARY_EXPRESSION, (ast, source, targetParent) ->
                new UnaryExpressionConverter(converters).convert(ast, source, targetParent));
        //type
        this.converters.put(Constants.ID_TYPE, (ast, source, targetParent) ->
                new IDTypeConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.STRING_TYPE, (ast, source, targetParent) ->
                new StringTypeConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.INT_TYPE, (ast, source, targetParent) ->
            new IntTypeConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.DOUBLE_TYPE, (ast, source, targetParent) ->
                new DoubleTypeConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.ENTITY_TYPE, (ast, source, targetParent) ->
                new EntityTypeConverter(converters).convert(ast,source,targetParent));
        this.converters.put(Constants.TUPLE_TYPE, (ast, source, targetParent) ->
                new TupleTypeConverter(converters).convert(ast,source,targetParent));
        this.converters.put(Constants.QUESTION_TYPE, (ast, source, targetParent) ->
                new QuestionTypeConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.DECLARED_TYPE, (ast, source, targetParent) ->
                new DeclaredTypeConverter(converters).convert(ast,source,targetParent));
        this.converters.put(Constants.SUM_TYPE, (ast, source, targetParent) ->
                this.converters.get(Constants.DECLARED_TYPE).convert(ast,source,targetParent));
        this.converters.put(Constants.FUNC_TYPE, (ast, source, targetParent) ->
                new FuncTypeConverter(converters).convert(ast,source,targetParent));
        this.converters.put(Constants.ARRAY_TYPE, (ast, source, targetParent) ->
                new ArrayTypeConverter(converters).convert(ast,source,targetParent));
        this.converters.put(Constants.LITERAL_INT_TYPE, (ast, source, targetParent) ->
                new LiteralIntTypeConverter(converters).convert(ast,source,targetParent));
        this.converters.put(Constants.LITERAL_STRING_TYPE, (ast, source, targetParent) ->
                new LiteralStringTypeConverter(converters).convert(ast,source,targetParent));

        //wrapper
        this.converters.put(Constants.ARGUMENT, (ast, source, targetParent) ->
                new ArgumentConverter(converters).convert(ast,source,targetParent));
        this.converters.put(Constants.REFERENCE_TYPE, (ast, source, targetParent) ->
                new ReferenceTypeConverter(converters).convert(ast,source,targetParent));
        //literal
        this.converters.put(Constants.STRING, (ast, source, targetParent) ->
                new StringLiteralConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.DOUBLE, (ast, source, targetParent) ->
                new DoubleLiteralConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.FLOAT, (ast, source, targetParent) ->
                new FloatLiteralConverter().convert(ast,source,targetParent));
        this.converters.put(Constants.INT, (ast, source, targetParent) ->
                new IntLiteralConverter().convert(ast,source,targetParent));
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
        this.converters.put(Constants.TERM_EXPRESSION, (ast, source, targetParent) ->
                new TermExprConverter(converters).convert(ast, source, targetParent));
        //body
        this.converters.put(Constants.BLOCK, (ast, source, targetParent) ->
                new BlockConverter(converters).convert(ast, source, targetParent));
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
        //poly expression
        this.converters.put(Constants.POLY_EXPRESSION, (ast, source, targetParent) ->
                new PolyExprConverter(converters).convert(ast, source, targetParent));
        //function call
        this.converters.put(Constants.FUNCTION_CALL, (ast, source, targetParent) ->
                new FunctionCallConverter(converters).convert(ast, source, targetParent));
        //if expression
        this.converters.put(Constants.IF_EXPRESSION, (ast, source, targetParent) ->
                new IfConverter(converters).convert(ast, source, targetParent));
        this.converters.put(Constants.Consequence, (ast, source, targetParent) ->
                new ConsequenceConverter(converters).convert(ast, source, targetParent));
        this.converters.put(Constants.EQUALITY_EXPRESSION, (ast, source, targetParent) ->
                new EqualityExprConverter(converters).convert(ast, source, targetParent));
        this.converters.put(Constants.TERNARY_EXPRESSION, (ast, source, targetParent) ->
                new TernaryExprConverter(converters).convert(ast, source, targetParent));
        this.converters.put(Constants.ARRAY, (ast, source, targetParent) ->
                new ArrayNodeConverter(converters).convert(ast, source, targetParent));
        this.converters.put(Constants.ARRAY_ENUM_ITEMS, (ast, source, targetParent) ->
                new ArrayEnumItemsConverter(converters).convert(ast, source, targetParent));
        this.converters.put(Constants.ARRAY_ITERATE_RANGE, (ast, source, targetParent) ->
                new ArrayRangeConverter(converters).convert(ast, source, targetParent));
        this.converters.put(Constants.ARRAY_ITERATE_EXPRESSION, (ast, source, targetParent) ->
                new ArrayExpressionConverter(converters).convert(ast, source, targetParent));
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



