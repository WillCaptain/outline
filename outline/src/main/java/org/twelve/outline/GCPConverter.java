package org.twelve.outline;

import org.twelve.gcp.ast.Node;
import org.twelve.msll.parsetree.ParseNode;
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
        this.converters.put(Constants.ROOT, new ProgramConverter(converters));
        //module
        this.converters.put(Constants.MODULE_STATEMENT, new ModuleConverter(converters));
        //import
        this.converters.put(Constants.IMPORT_STATEMENT, new ImportConverter(converters));
        //export
        this.converters.put(Constants.EXPORT_STATEMENT, new ExportConverter(converters));
        this.converters.put(Constants.EXPRESSION, new ExpressionConverter(converters));
        //variable declarator
        this.converters.put(Constants.VARIABLE_DECLARATOR, new VarDeclareConverter(converters));
        this.converters.put(Constants.ASSIGNMENT, new AssignmentConverter(converters));
        this.converters.put(Constants.EXPRESSION_STATEMENT, new ExpresionStatementConverter(converters));
        this.converters.put(Constants.UNARY_EXPRESSION, new UnaryExpressionConverter(converters));
        //type
        this.converters.put(Constants.ID_TYPE, new IDTypeConverter(converters));
        this.converters.put(Constants.STRING_TYPE, new StringTypeConverter(converters));
        this.converters.put(Constants.INT_TYPE, new IntTypeConverter(converters));
        this.converters.put(Constants.LONG_TYPE, new LongTypeConverter(converters));
        this.converters.put(Constants.DOUBLE_TYPE, new DoubleTypeConverter(converters));
        this.converters.put(Constants.ENTITY_TYPE, new EntityTypeConverter(converters));
        this.converters.put(Constants.TUPLE_TYPE, new TupleTypeConverter(converters));
        this.converters.put(Constants.QUESTION_TYPE, new QuestionTypeConverter(converters));
        DeclaredTypeConverter declaredTypeConverter = new DeclaredTypeConverter(converters);
        this.converters.put(Constants.DECLARED_TYPE, declaredTypeConverter);
        this.converters.put(Constants.SUM_TYPE, declaredTypeConverter);
        this.converters.put(Constants.FUNC_TYPE, new FuncTypeConverter(converters));
        this.converters.put(Constants.ARRAY_TYPE, new ArrayTypeConverter(converters));
        this.converters.put(Constants.MAP_TYPE, new MapTypeConverter(converters));
        //literal
        LiteralTypeConverter literalTypeConverter = new LiteralTypeConverter(converters);
        this.converters.put(Constants.LITERAL_INT_TYPE, literalTypeConverter);
        this.converters.put(Constants.LITERAL_STRING_TYPE, literalTypeConverter);
        this.converters.put(Constants.LITERAL_ENTITY_TYPE, literalTypeConverter);
        this.converters.put(Constants.LITERAL_TUPLE_TYPE, literalTypeConverter);
        //true, false
        BoolTypeConverter boolTypeConverter = new BoolTypeConverter(converters);
        this.converters.put(Constants.True,boolTypeConverter);
        this.converters.put(Constants.False,boolTypeConverter);
        //wrapper
        this.converters.put(Constants.ARGUMENT, new ArgumentConverter(converters));
        this.converters.put(Constants.REFERENCE_TYPE, new ReferenceTypeConverter(converters));
        //literal
        this.converters.put(Constants.STRING, new StringLiteralConverter(converters));
        this.converters.put(Constants.DOUBLE, new DoubleLiteralConverter(converters));
        this.converters.put(Constants.FLOAT, new FloatLiteralConverter(converters));
        this.converters.put(Constants.INT, new IntLiteralConverter(converters));
        this.converters.put(Constants.NUMBER, new NumberLiteralConverter(converters));
        //id
        this.converters.put(Constants.ID, new Converter(converters) {
            @Override
            public Node convert(AST ast, ParseNode source, Node related) {
                return new Identifier(ast, convertStrToken(((TerminalNode) source).token()));
            }
        });

        //function
        this.converters.put(Constants.FUNCTION, new FunctionConverter(converters));
        //numeric expression
        this.converters.put(Constants.NUMERIC_EXPRESSION, new NumericExprConverter(converters));
        this.converters.put(Constants.TERM_EXPRESSION, new TermExprConverter(converters));
        //body
        this.converters.put(Constants.BLOCK, new BlockConverter(converters));
        //complex expression: entity extension
        this.converters.put(Constants.COMPLEX_EXPRESSION, new EntityExtensionConverter(converters));

        //entity
        this.converters.put(Constants.ENTITY, new EntityConverter(converters));
        //entity property assignment
        this.converters.put(Constants.PROPERTY_ASSIGNMENT, new PropertyAssignmentConverter(converters));
        //tuple
        this.converters.put(Constants.TUPLE, new TupleConverter(converters));
        //factor expression
        this.converters.put(Constants.FACTOR_EXPRESSION, new FactorExprConverter(converters));
        //factor (expression)
        this.converters.put(Constants.FACTOR, new FactorConverter(converters));
        //this
        this.converters.put(Constants.THIS, new ThisConverter(converters));
        //base
        this.converters.put(Constants.BASE, new BaseConverter(converters));
        //member accessor
        this.converters.put(Constants.MEMBER_ACCESSOR, new MemberAccessorConverter(converters));
        //poly expression
        this.converters.put(Constants.POLY_OPTION_EXPRESSION, new PolyOptionExprConverter(converters));
        //function call
        this.converters.put(Constants.FUNCTION_CALL, new FunctionCallConverter(converters));
        //reference call
        this.converters.put(Constants.REFERENCE_CALL, new ReferenceCallConverter(converters));
        //array map accessor
        this.converters.put(Constants.ARRAY_MAP_ACCESSOR, new AccessorConverter(converters));
        //if expression
        this.converters.put(Constants.IF_EXPRESSION, new IfConverter(converters));
        this.converters.put(Constants.Consequence, new ConsequenceConverter(converters));
        this.converters.put(Constants.EQUALITY_EXPRESSION, new EqualityExprConverter(converters));
        this.converters.put(Constants.TERNARY_EXPRESSION, new TernaryExprConverter(converters));
        //array
        this.converters.put(Constants.ARRAY, new ArrayNodeConverter(converters));
        this.converters.put(Constants.ARRAY_ENUM_ITEMS, new ArrayEnumItemsConverter(converters));
        this.converters.put(Constants.ARRAY_ITERATE_RANGE, new ArrayRangeConverter(converters));
        this.converters.put(Constants.ARRAY_ITERATE_EXPRESSION, new ArrayExpressionConverter(converters));
        //map
        this.converters.put(Constants.MAP, new MapNodeConverter(converters));
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



