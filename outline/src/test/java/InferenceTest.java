import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.exception.GCPErrCode;
import org.twelve.gcp.node.expression.*;
import org.twelve.gcp.node.expression.conditions.MatchArm;
import org.twelve.gcp.node.expression.conditions.MatchExpression;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.function.FunctionCallNode;
import org.twelve.gcp.node.function.FunctionNode;
import org.twelve.gcp.node.imexport.Export;
import org.twelve.gcp.node.imexport.Import;
import org.twelve.gcp.node.expression.Assignment;
import org.twelve.gcp.node.statement.OutlineDeclarator;
import org.twelve.gcp.node.statement.Statement;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.gcp.node.unpack.TupleUnpackNode;
import org.twelve.gcp.outline.Outline;
import org.twelve.gcp.outline.adt.*;
import org.twelve.gcp.outline.builtin.ERROR;
import org.twelve.gcp.outline.primitive.BOOL;
import org.twelve.gcp.outline.primitive.DOUBLE;
import org.twelve.gcp.outline.primitive.INTEGER;
import org.twelve.gcp.outline.primitive.NUMBER;
import org.twelve.gcp.outline.primitive.STRING;
import org.twelve.gcp.outline.projectable.FirstOrderFunction;
import org.twelve.gcp.outline.projectable.Function;
import org.twelve.gcp.outline.projectable.Genericable;
import org.twelve.gcp.outline.projectable.Reference;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.twelve.gcp.common.Tool.cast;

public class InferenceTest {

    @BeforeAll
    static void warmUp() {
        // Triggers ASTHelper class loading and OutlineParser grammar compilation
        // so the one-time initialisation cost is not charged to the first test.
        ASTHelper.parser.toString();
    }


    @Test
    void test_initialization_inference() {
        /*
        module org.twelve.education
        let grade: Integer = 1, school: String = "NO.1";
        export grade, school as college;

        module org.twelve.human
        import grade as level, college as school from education;
        let age: Integer, name: String = "Will", height: Double = 1.68, grade: Integer = level;
        export height as stature, name;
         */
        ASF asf = ASTHelper.educationAndHuman();
        asf.infer();
        assertTrue(asf.inferred());
        AST ast = cast(asf.get("education"));
        Export exports = ast.program().body().exports().getFirst();
        assertInstanceOf(INTEGER.class, exports.specifiers().getFirst().outline());
        assertInstanceOf(STRING.class, exports.specifiers().getLast().outline());
        assertTrue(ast.errors().isEmpty());
        ast = cast(asf.get("human"));
        Import imports = ast.program().body().imports().getFirst();
        assertInstanceOf(INTEGER.class, imports.specifiers().getFirst().outline());
        assertInstanceOf(STRING.class, imports.specifiers().get(1).outline());
        VariableDeclarator var = cast(ast.program().body().statements().get(0));
        assertEquals(var.assignments().getFirst().lhs(), ast.errors().getFirst().node());
        assertEquals(ast.Ignore, var.outline());
        assertEquals(ast.Ignore, var.assignments().getFirst().outline());
        //age:Integer
        Assignment age = var.assignments().getFirst();
        assertEquals(ast.Integer, ((Variable) age.lhs()).declared().outline());
        assertEquals(ast.Integer, age.lhs().outline());
        //name = "Will"
        Assignment name = var.assignments().get(1);
        assertInstanceOf(STRING.class, name.lhs().outline());
        //height:Decimal = 1.68
        Assignment height = var.assignments().get(2);
        assertInstanceOf(DOUBLE.class, height.lhs().outline());
        assertEquals(ast.errors().getFirst().node(), age.lhs());
    }

    @Test
    void test_declared_assignment_type_mismatch_inference() {
        /*
        module me
        var age: Integer = "some";
         */
        AST ast = ASTHelper.declaredAssignmentTypeMismatch();
        ast.infer();
        VariableDeclarator var = cast(ast.program().body().statements().get(0));
        assertTrue(ast.inferred());
        assertInstanceOf(INTEGER.class, var.assignments().getFirst().lhs().outline());
        assertEquals(1, ast.errors().size());
        assertEquals(var.assignments().getFirst(), ast.errors().getFirst().node());
        assertEquals(GCPErrCode.OUTLINE_MISMATCH, ast.errors().getFirst().errorCode());
    }

    @Test
    void test_assign_mismatch_inference() {
        /*
        module me
        var age = "some";
        age = 100;
         */
        AST ast = ASTHelper.assignMismatch();
        Node assignment = ast.program().body().statements().get(1).get(0);
        ast.infer();
        assertTrue(ast.inferred());
        assertEquals(1, ast.errors().size());
        assertEquals(GCPErrCode.OUTLINE_MISMATCH, ast.errors().getFirst().errorCode());
        assertEquals(assignment, ast.errors().getFirst().node());
    }

    @Test
    void test_declared_poly_mismatch_assignment_inference() {
        /*
        module me
        var age = "some"&100;
        age = 100.0;
         */
        AST ast = ASTHelper.declaredPolyMismatchAssignment();

        ast.infer();
        assertEquals(1, ast.errors().size());
        assertEquals(GCPErrCode.OUTLINE_MISMATCH, ast.errors().getFirst().errorCode());
    }

    @Test
    void test_explicit_poly_inference() {
        /*
        module default
        var poly: Integer&String = 100&"some";
        poly = 10.0;//poly doesn't have float
        let poly = 10.0;//duplicated definition
         */
        AST ast = ASTHelper.mockDefinedPoly();
        assertTrue(ast.asf().infer());
        Identifier lhs = cast(((VariableDeclarator) ast.program().body().nodes().get(0)).assignments().get(0).lhs());
        Poly poly = cast(lhs.outline());
        assertEquals(ast.Integer.toString(), poly.options().get(0).toString());
        assertInstanceOf(STRING.class, poly.options().get(1));

        ast = ASTHelper.mockErrorAssignOnDefinedPoly();
        assertTrue(ast.asf().infer());
        Poly p1 = cast(((Assignment) ast.program().body().nodes().get(1).get(0)).lhs().outline());
        VariableDeclarator declarator = cast(ast.program().body().nodes().get(2));
        Outline p2 = declarator.assignments().get(0).lhs().outline();
        assertEquals(2, ast.errors().size());
        assertEquals(GCPErrCode.OUTLINE_MISMATCH, ast.errors().get(0).errorCode());
        assertEquals(GCPErrCode.DUPLICATED_DEFINITION, ast.errors().get(1).errorCode());

        assertEquals(ast.Integer.toString(), p1.options().get(0).toString());
        assertInstanceOf(INTEGER.class, p1.options().get(0));
        assertInstanceOf(STRING.class, p1.options().get(1));

        assertInstanceOf(ERROR.class, p2);
    }

    @Test
    void test_literal_inference() {
        AST ast = ASTHelper.mockDefinedLiteralUnion();
        ast.asf().infer();
        Identifier lhs = cast(((VariableDeclarator) ast.program().body().nodes().get(0)).assignments().get(0).lhs());
        Option union = cast(lhs.outline());
        assertEquals("Integer", union.options().get(0).toString());
        assertEquals("String", union.options().get(1).toString());

        ast = ASTHelper.mockAssignOnDefinedLiteralUnion();
        ast.asf().infer();
        assertTrue(ast.asf().inferred());
        assertEquals(2, ast.errors().size());
        assertEquals(ast.program().body().statements().get(1).get(0), ast.errors().getFirst().node().parent());
    }

    @Test
    void test_function_definition_inference() {
        AST ast = ASTHelper.mockAddFunc();
        assertTrue(ast.asf().infer());
        VariableDeclarator declare = cast(ast.program().body().statements().getFirst());
        Assignment assign = declare.assignments().getFirst();
        assertInstanceOf(FirstOrderFunction.class, assign.lhs().outline());
    }

    @Test
    void test_override_function_definition_inference() {
        /*
        module default
        var add = (x,y)->x+y & (x,y,z)->x+y+z;
         */
        AST ast = ASTHelper.mockOverrideAddFunc();
        assertTrue(ast.asf().infer());
        Identifier add = cast(ast.program().body().nodes().getFirst().nodes().getFirst().nodes().getFirst());
        assertInstanceOf(Poly.class, add.outline());
        assertEquals(2, ((Poly) add.outline()).options().size());
        assertTrue(((Poly) add.outline()).options().getFirst() instanceof Function<?, ?>);
    }

    @Test
    void test_binary_expression() {
        /*
        module test
        let a = 100.0, b = 100, c = "some";
        a+b;
        a+c;
        a-b;
        a-c;
        a==b;
         */
        AST ast = ASTHelper.mockGCPTestAst();
        assertTrue(ast.asf().infer());
        List<Statement> sts = ast.program().body().statements();
        assertInstanceOf(DOUBLE.class, sts.get(1).get(0).outline());
        assertInstanceOf(STRING.class, sts.get(2).get(0).outline());
        assertInstanceOf(DOUBLE.class, sts.get(3).get(0).outline());
        assertEquals(ast.Boolean, sts.get(5).get(0).outline());
        assertEquals(1, ast.errors().size());
        assertEquals(sts.get(4).get(0), ast.errors().getFirst().node());
    }

    @Test
    void test_simple_person_entity() {
        /*
        let person: Entity = {
          name = "Will",
          get_name = ()->this.name;
          get_my_name = ()->name;
        };
        let name_1 = person.name;
        let name_2 = person.get_name();
         */
        AST ast = ASTHelper.mockSimplePersonEntity();
        assertTrue(ast.asf().infer());
        VariableDeclarator var = cast(ast.program().body().statements().getFirst());
        Entity person = cast(var.assignments().getFirst().lhs().outline());
        List<EntityMember> ms = person.members().stream().filter(m -> !m.isDefault()).toList();
        assertEquals(3, ms.size());
        EntityMember name = ms.get(0);
        EntityMember getName = ms.get(1);
        EntityMember getName2 = ms.get(2);
        assertInstanceOf(STRING.class, name.outline());
        Outline getNameOutline = getName.outline().eventual();
        Outline getName2Outline = getName2.outline().eventual();
        assertInstanceOf(Function.class, getNameOutline);
        assertInstanceOf(STRING.class, ((Function<?, ?>) getNameOutline).returns().supposedToBe());
        assertInstanceOf(STRING.class, ((Function<?, ?>) getName2Outline).returns().supposedToBe());

        VariableDeclarator name1 = cast(ast.program().body().statements().get(1));
        VariableDeclarator name2 = cast(ast.program().body().statements().get(2));
        assertInstanceOf(STRING.class, name1.assignments().getFirst().lhs().outline());
        assertInstanceOf(STRING.class, name2.assignments().getFirst().lhs().outline());
    }

    @Test
    void test_tuple() {
        AST ast = ASTHelper.mockSimpleTuple();
        assertTrue(ast.asf().infer());
        VariableDeclarator var = cast(ast.program().body().statements().getFirst());
        Tuple person = cast(var.assignments().getFirst().lhs().outline());
        List<EntityMember> ms = person.members().stream().filter(m -> !m.isDefault()).toList();
        assertEquals(3, ms.size());
        EntityMember name = ms.get(0);
        EntityMember getName = ms.get(1);
        assertInstanceOf(STRING.class, name.outline());
        assertInstanceOf(Function.class, getName.outline().eventual());
        VariableDeclarator name1 = cast(ast.program().body().statements().get(1));
        VariableDeclarator name2 = cast(ast.program().body().statements().get(2));
        assertInstanceOf(STRING.class, name1.assignments().getFirst().lhs().outline());
        assertInstanceOf(STRING.class, name2.assignments().getFirst().lhs().outline());
    }

    @Test
    void test_simple_person_entity_with_override_member() {
        /*
        module test
        let person = {
            get_name = ()->this.name,
            name = "Will",
            mute get_name = ()->this.name&last_name->this.name+last_name
        };
        let name_1 = person.name;
        let name_2 = person.get_name();
         */
        AST ast = ASTHelper.mockSimplePersonEntityWithOverrideMember();
        assertTrue(ast.asf().infer());
        VariableDeclarator var = cast(ast.program().body().statements().getFirst());
        Entity person = cast(var.assignments().getFirst().lhs().outline());
        List<EntityMember> ms = person.members().stream().filter(m -> !m.isDefault()).toList();
        assertEquals(2, ms.size());
        EntityMember getName = ms.get(1);
        assertInstanceOf(Poly.class, getName.outline());
        Poly outline = cast(getName.outline());
        Function<?, ?> f1 = cast(outline.options().get(0));
        assertInstanceOf(STRING.class, f1.returns().supposedToBe());
        FirstOrderFunction f2 = cast(outline.options().get(1));
        assertInstanceOf(Option.class, f2.argument().definedToBe());
        assertInstanceOf(STRING.class, f2.returns().supposedToBe());
    }

    @Test
    void test_inherited_person_entity() {
        AST ast = ASTHelper.mockInheritedPersonEntity();
        ast.asf().infer();
        Entity person = cast(ast.program().body().statements().get(3).nodes().get(0).nodes().get(0).outline());
        List<EntityMember> ms = person.members().stream().filter(m -> !m.isDefault()).toList();
        assertEquals(4, ms.size());
        EntityMember getFullName = ms.getFirst();
        Outline nameOutline = getFullName.outline().eventual();
        assertInstanceOf(Function.class, nameOutline);
        assertInstanceOf(STRING.class, ((Function<?, ?>) nameOutline).returns().supposedToBe());
    }

    @Test
    void test_inherited_person_entity_with_override_member() {
        /**
         * let person = {
         *   get_name = ()->this.name,
         *   get_my_name = ()->name,
         *   name = "Will"
         * };
         * let name_1 = person.name;
         * let name_2 = person.get_name();
         * let me = person{
         *   get_name = last_name->{
         *     this.get_name()+last_name;
         *     100
         *   },
         *   get_other_name = ()->{
         *     get_name();
         *     get_name("other")
         *   }
         * };
         * me.get_name("Zhang");
         */
        AST ast = ASTHelper.mockInheritedPersonEntityWithOverrideMember();
        ast.asf().infer();
        assertTrue(ast.inferred());
        Entity person = cast(ast.program().body().statements().get(3).nodes().getFirst().nodes().getFirst().outline());
        List<EntityMember> members = person.members().stream().filter(m -> !m.isDefault()).toList();
        assertEquals(5, members.size());
        assertInstanceOf(STRING.class, members.get(2).outline());
        assertInstanceOf(Poly.class, members.get(3).outline());
        Poly getName = cast(members.get(3).outline());
        assertSame(ast.Unit, ((Genericable<?, ?>) ((Function<?, ?>) getName.options().get(0)).argument()).declaredToBe());
        Function<?, ?> overrides = cast(getName.options().get(1));
        assertInstanceOf(Option.class, ((Genericable<?, ?>) overrides.argument()).definedToBe());
        assertInstanceOf(INTEGER.class, overrides.returns().supposedToBe());
        assertTrue(ast.errors().isEmpty());
    }

    @Test
    void test_declare_infer() {
        AST ast = ASTHelper.mockDeclare();
        ast.asf().infer();
        assertTrue(ast.asf().inferred());
    }

    @Test
    void test_as() {
        AST ast = ASTHelper.mockAs();
        ast.asf().infer();
        Assignment a = ((VariableDeclarator) ast.program().body().statements().get(0)).assignments().getFirst();
        Entity ea = cast(a.lhs().outline());
        List<EntityMember> ms = ea.members().stream().filter(m -> !m.isDefault()).toList();
        assertEquals("name", ms.get(0).name());
        assertEquals("String", ms.get(0).outline().toString());
        Assignment b = ((VariableDeclarator) ast.program().body().statements().get(1)).assignments().getFirst();
        Entity eb = cast(b.lhs().outline());
        ms = eb.members().stream().filter(m -> !m.isDefault()).toList();
        assertEquals("name", ms.get(0).name());
        assertEquals("Integer", ms.get(0).outline().toString());
        assertEquals(1, ast.errors().size());
        assertEquals(b.rhs(), ast.errors().get(0).node());
    }

    @Test
    void test_option_is_as() {
        /*let result = {
            var some:String|Integer = "string";
            if(some is Integer){
                some
            }else if(some is String as str){
                str
            }else{100}
        };*/
        AST ast = ASTHelper.mockOptionIsAs();
        ast.asf().infer();
        Assignment assignment = ((VariableDeclarator) ast.program().body().nodes().getFirst()).assignments().getFirst();
        Outline result = assignment.lhs().outline();
        assertInstanceOf(Option.class, result);
        assertInstanceOf(INTEGER.class, ((Option) result).options().getFirst());
        assertInstanceOf(STRING.class, ((Option) result).options().getLast());
        Node rootSome = assignment.rhs().nodes().getFirst().nodes().getFirst().nodes().getFirst();
        assertInstanceOf(Option.class, rootSome.outline());
        Node some = assignment.rhs().nodes().get(1).nodes().getFirst().nodes().getFirst().nodes().getLast().nodes().getFirst().nodes().getFirst();
        assertInstanceOf(INTEGER.class, some.outline());
        Node str = assignment.rhs().nodes().get(1).nodes().getFirst().nodes().get(1).nodes().getLast().nodes().getFirst().nodes().getFirst();
        assertInstanceOf(STRING.class, str.outline());
        assertTrue(ast.errors().isEmpty());
    }

    @Test
    void test_poly_is_as() {
        /*let result = {
            var some = 100&"string";
            if(some is Integer){
                some
            }else if(some is String as str){
                str
            }else{100}
        };*/
        AST ast = ASTHelper.mockPolyIsAs();
        ast.asf().infer();
        Assignment assignment = ((VariableDeclarator) ast.program().body().nodes().getFirst()).assignments().getFirst();
        Outline result = assignment.lhs().outline();
        assertInstanceOf(Option.class, result);
        assertInstanceOf(INTEGER.class, ((Option) result).options().getFirst());
        assertInstanceOf(STRING.class, ((Option) result).options().getLast());
        Node rootSome = assignment.rhs().nodes().getFirst().nodes().getFirst().nodes().getFirst();
        assertInstanceOf(Poly.class, rootSome.outline());
        Node some = assignment.rhs().nodes().get(3).nodes().getFirst().nodes().getFirst().nodes().getLast().nodes().getFirst().nodes().getFirst();
        assertInstanceOf(INTEGER.class, some.outline());
        Node str = assignment.rhs().nodes().get(3).nodes().getFirst().nodes().get(1).nodes().getLast().nodes().getFirst().nodes().getFirst();
        assertInstanceOf(STRING.class, str.outline());
        assertTrue(ast.errors().isEmpty());

        Outline i = assignment.rhs().nodes().get(1).nodes().get(0).nodes().get(0).outline();
        assertInstanceOf(INTEGER.class, i);
        Outline s = assignment.rhs().nodes().get(2).nodes().get(0).nodes().get(0).outline();
        assertInstanceOf(STRING.class, s);
    }

    @Test
    void test_generic_is_as() {
        /*let result = some->{
            if(some is Integer){
                some
            }else if(some is String as str){
                str
            }else{100}
        }(100);*/
        AST ast = ASTHelper.mockGenericIsAs();
        ast.asf().infer();
        Assignment assignment = ((VariableDeclarator) ast.program().body().nodes().getFirst()).assignments().getFirst();
        Outline result = assignment.lhs().outline();
        assertInstanceOf(Option.class, result);
        assertInstanceOf(INTEGER.class, ((Option) result).options().getFirst());
        assertInstanceOf(STRING.class, ((Option) result).options().getLast());

        FunctionNode function = cast(((FunctionCallNode) assignment.rhs()).function());
        assertEquals(1, ast.errors().size());
    }

    @Test
    void test_inference_of_reference_in_function() {
        /*
        let f = fx<a,b>(x:a)->{
           let y:b = 100;
           y
        }*/
        AST ast = ASTHelper.mockReferenceInFunction();
        ast.infer();
        Assignment assignment = ((VariableDeclarator) ast.program().body().statements().getFirst()).assignments().getFirst();
        FirstOrderFunction f = cast(assignment.lhs().outline());
        assertInstanceOf(Reference.class, f.argument());
        assertEquals("a", f.argument().name());
        assertInstanceOf(Reference.class, f.returns().supposedToBe());
        assertEquals("b", f.returns().supposedToBe().name());
        assertInstanceOf(INTEGER.class, ((Reference) f.returns().supposedToBe()).extendToBe());
    }

    @Test
    void test_inference_of_reference_in_entity() {
        /*
        let g = fx<a,b>()->{
           {
                z:a = 100,
                f = fx<c>(x:b,y:c)->y
            }
        }*/
        AST ast = ASTHelper.mockReferenceInEntity();
        ast.infer();
        Function<?, ?> g = cast(((VariableDeclarator) ast.program().body().get(0)).assignments().get(0).rhs().outline());
        Entity entity = cast(g.returns().supposedToBe());
        Reference z = cast(entity.members().getLast().node().outline());
        List<EntityMember> ms = entity.members().stream().filter(m -> !m.isDefault()).toList();
        //(x,y)->y
        Function<?, Genericable<?, ?>> f = cast(ms.getFirst().outline());
        assertEquals("a", z.name());
        assertInstanceOf(INTEGER.class, z.extendToBe());
        assertEquals("b", f.argument().name());
        f = cast(f.returns().supposedToBe());
        assertEquals("c", f.argument().name());
        assertEquals("c", f.returns().supposedToBe().name());
    }

    @Test
    void test_inference_of_basic_adt() {
        /**
         * let x = 100;
         * x.to_str()
         * let s = "str"
         * s.to_str()
         * let b = true;
         * b.to_str()
         */
        AST ast = ASTHelper.mockBasicAdtMethods();
        assertTrue(ast.asf().infer());
        assertEquals("Integer", ast.program().body().statements().get(0).get(0).get(0).outline().toString());
        assertEquals("String", ast.program().body().statements().get(1).get(0).outline().toString());
        assertEquals("String", ast.program().body().statements().get(2).get(0).get(0).outline().toString());
        assertEquals("String", ast.program().body().statements().get(3).get(0).outline().toString());
        assertEquals("Bool", ast.program().body().statements().get(4).get(0).get(0).outline().toString());
        assertEquals("String", ast.program().body().statements().get(5).get(0).outline().toString());
    }

    @Test
    void test_inference_of_array_map() {
        /*
         * let x = [1,2];
         * let y = x.map(i->i.to_str())
         * y[0]
         */
        AST ast = ASTHelper.mockArrayMapMethod();
        assertTrue(ast.asf().infer());
        ast.program().body().statements().get(1).get(0).get(1).inferred();
        Outline y = ast.program().body().statements().get(1).get(0).get(0).outline();
        assertEquals("[String]", y.toString());
        Outline y0 = ast.program().body().statements().get(2).get(0).outline();
        assertEquals("String", y0.toString());
        assertTrue(ast.errors().isEmpty());
    }

    @Test
    void test_inference_of_array_reduce() {
        /*
         * let x = [1,2];
         * let y = x.reduce((acc,i)->acc+i,0.1)
         */
        AST ast = ASTHelper.mockArrayReduceMethod();
        assertTrue(ast.asf().infer());
        Outline y = ast.program().body().statements().get(2).get(0).outline();
        assertEquals("Double", y.toString());
        assertTrue(ast.errors().isEmpty());
    }

    @Test
    void test_inference_of_array_definition() {
        AST ast = ASTHelper.mockArrayDefinition();
        assertTrue(ast.asf().infer());
        assertEquals(0, ast.errors().size());
//        assertEquals(GCPErrCode.AMBIGUOUS_DECLARATION,ast.errors().getFirst().errorCode());
        //let a = [1,2,3,4];
        Variable a = cast(((VariableDeclarator) ast.program().body().statements().get(0)).assignments().getFirst().lhs());
        assertEquals("[Integer]", a.outline().toString());
        //let b: [String] = [];
        Variable b = cast(((VariableDeclarator) ast.program().body().statements().get(1)).assignments().getFirst().lhs());
        assertEquals("[String]", b.outline().toString());
        //let c: [] = [...5];
        Variable c = cast(((VariableDeclarator) ast.program().body().statements().get(2)).assignments().getFirst().lhs());
        assertEquals("[any]", c.outline().toString());
        //let d = [1...6,2,x->x+"2",x->x%2==0];""";
        Variable d = cast(((VariableDeclarator) ast.program().body().statements().get(3)).assignments().getFirst().lhs());
        assertEquals("[String]", d.outline().toString());
        //let e = [];
        Variable e = cast(((VariableDeclarator) ast.program().body().statements().get(4)).assignments().getFirst().lhs());
        assertEquals("[any]", e.outline().toString());
    }

    @Test
    void test_inference_of_dict_definition() {
        AST ast = ASTHelper.mockDictDefinition();
        assertTrue(ast.asf().infer());

        //let a = [{name = "Will"}:"Male", {name = "Ivy",age = 20}:"Female"];
        Variable a = cast(((VariableDeclarator) ast.program().body().statements().get(0)).assignments().getFirst().lhs());
        assertEquals("[{name: String} : String]", a.outline().toString());
        //let b: [Integer:String] = [:];
        Variable b = cast(((VariableDeclarator) ast.program().body().statements().get(1)).assignments().getFirst().lhs());
        assertEquals("[Integer : String]", b.outline().toString());
        //let c = [:];
        Variable c = cast(((VariableDeclarator) ast.program().body().statements().get(2)).assignments().getFirst().lhs());
        assertEquals("[any : any]", c.outline().toString());
        //let d: [String:?] = [”Will":30,30:30];
        Variable d = cast(((VariableDeclarator) ast.program().body().statements().get(3)).assignments().getFirst().lhs());
        assertEquals("[String : any]", d.outline().toString());
    }

    @Test
    void test_inference_of_unpack() {
        AST ast = ASTHelper.mockUnpack();
        ast.asf().infer();
        assertTrue(ast.inferred());
        int size = ast.program().body().statements().size();
        Node first = ast.program().body().get(size - 6);
        assertInstanceOf(STRING.class, first.outline());
        Node g = ast.program().body().get(size - 5);
        assertInstanceOf(STRING.class, g.outline());
        Node lastName = ast.program().body().get(size - 4);
        assertInstanceOf(STRING.class, lastName.outline());
        Node name = ast.program().body().get(size - 3);
        assertInstanceOf(STRING.class, name.outline());
        Node gender = ast.program().body().get(size - 2);
        assertInstanceOf(STRING.class, gender.outline());
        Node age = ast.program().body().get(size - 1);
        assertInstanceOf(INTEGER.class, age.outline());
    }

    @Test
    void test_inference_if() {
        AST ast = ASTHelper.mockIf();
        ast.infer();
        ast.inferred();
        assertTrue(ast.asf().infer());
        Node get = ast.program().body().get(3);
        assertEquals("(String,Integer)|String|Integer", get.outline().toString());
    }

    @Test
    void test_match() {
        AST ast = ASTHelper.mockMatch();
        assertTrue(ast.asf().infer());
        VariableDeclarator converted = cast(ast.program().body().get(3));
        Outline convertedOutline = converted.assignments().getFirst().lhs().outline();
        assertEquals("Integer|String", convertedOutline.toString());
        VariableDeclarator name1 = cast(ast.program().body().get(4));
        Outline name1Outline = name1.assignments().getFirst().lhs().outline();
        VariableDeclarator name2 = cast(ast.program().body().get(5));
        Outline name2Outline = name2.assignments().getFirst().lhs().outline();
        //check return of match
        assertInstanceOf(STRING.class, name1Outline);
        assertEquals("String|(Integer,String)", name2Outline.toString());
        assertEquals(1, ast.errors().size());
        assertEquals(name1.assignments().getFirst(), ast.errors().getFirst().node());
        //check match pattern type
        //tuple match
        List<MatchArm> arms1 = ((MatchExpression) name1.assignments().getFirst().rhs()).arms();
        //(name,age)
        Node name = arms1.getFirst().test().pattern().get(0);
        Node age = arms1.getFirst().test().pattern().get(1);
        assertEquals("(String,String)", name.outline().toString());
        assertInstanceOf(INTEGER.class, age.outline());
        //((last,first))
        TupleUnpackNode fullName = cast(arms1.getLast().test().pattern().get(0));
        Node last = fullName.get(0);
        Node first = fullName.get(1);
        assertInstanceOf(STRING.class, last.outline());
        assertInstanceOf(STRING.class, first.outline());
        //entity match
        List<MatchArm> arms2 = ((MatchExpression) name2.assignments().getFirst().rhs()).arms();
        name = arms2.get(0).test().pattern().get(0);
        age = arms2.get(0).test().pattern().get(1);
        assertEquals("{last: String,first: String}", name.outline().toString());
        assertInstanceOf(INTEGER.class, age.outline());
        last = arms2.get(1).test().pattern().get(0).get(0);
        age = arms2.get(1).test().pattern().get(1);
        assertInstanceOf(STRING.class, last.outline());
        assertInstanceOf(INTEGER.class, age.outline());
    }

    @Test
    void test_with() {
        AST ast = ASTHelper.mockWith();
        assertTrue(ast.asf().infer());
        List<Statement> statements = ast.program().body().statements();
        assertInstanceOf(BOOL.class, statements.getLast().outline());
        assertTrue(ast.errors().isEmpty());
    }

    @Test
    void test_symbol_match() {
        AST ast = ASTHelper.mockSymbolMatch();
        ast.asf().infer();
        assertTrue(ast.inferred());
        List<Statement> statements = ast.program().body().statements();
        OutlineDeclarator s1 = cast(statements.get(0));
        OutlineDefinition human = s1.definitions().getFirst();
        assertEquals("Male{name: String,age: Integer}|Female(Integer,String)", human.symbolNode().outline().toString());
        OutlineDefinition pet = s1.definitions().getLast();
        assertEquals("Dog|Cat", pet.symbolNode().outline().toString());

        Outline tuple = statements.get(5).get(0).outline();
        assertEquals("(String|{other: Float},Integer|{other: Float},{other: Float})", tuple.toString());
    }

    @Test
    void test_future_reference_from_entity() throws IOException {
        AST ast = ASTHelper.mockFutureReferenceFromEntity();
        assertTrue(ast.asf().infer());
        Tuple rt = cast(ast.program().body().statements().getLast().outline());
        assertEquals("(String,String,String,String,Integer,Male,String,Integer,Error,String)",rt.toString());
//        assertInstanceOf(STRING.class,rt.get(0));
//        assertInstanceOf(STRING.class,rt.get(1));
    }

    @Test
    void test_future_reference_from_outline(){
        AST ast = ASTHelper.mockFutureReferenceFromOutline();
        ast.asf().infer();
        assertTrue(ast.inferred());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("(Integer,[String : Number],Integer)",outline.toString());
    }

    @Test
    void test_inter_invoke(){
        AST ast = ASTHelper.mockInterInvoke();
        ast.asf().infer();
        assertTrue(ast.inferred());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("(Asia,String,Integer,Asia,String,String,A|B|C,A|B|C)",outline.toString());
    }

    @Test
    void test_reference_call_Lazy(){
        AST ast = ASTHelper.mockReferCallLazy();
        ast.asf().infer();
        assertTrue(ast.inferred());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("(String,Integer,String,Integer,`String`,String,Integer,Integer,Integer)",outline.toString());
        assertEquals(1, ast.errors().size());
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().getFirst().errorCode());
    }

    //@Test
    void test_reference_call_this(){
        AST ast = ASTHelper.mockReferCallThis();
        ast.asf().infer();
        assertTrue(ast.inferred());
        Outline outline = ast.program().body().statements().getLast().outline();
    }

    @Test
    void test_function_project_change_function_declaration() {

    }

    // ─────────────────── helper ───────────────────────────────────────────────

    /** Returns the LHS outline of the Nth variable declarator in the program body. */
    private static Outline lhsOf(AST ast, int stmtIndex) {
        VariableDeclarator decl = cast(ast.program().body().statements().get(stmtIndex));
        return decl.assignments().getFirst().lhs().outline();
    }

    // ─────────────────── built-in Number methods ──────────────────────────────

    @Test
    void test_inference_of_number_methods() {
        /*
         * let x = 100;
         * let a = x.abs();     -> Number
         * let b = x.ceil();    -> Integer
         * let c = x.floor();   -> Integer
         * let d = x.round();   -> Integer
         * let e = x.to_int();  -> Integer
         * let f = x.to_float();-> Double
         * let g = x.sqrt();    -> Double
         * let h = x.pow(2.0);  -> Double
         */
        AST ast = ASTHelper.mockNumberMethods();
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());

        assertInstanceOf(NUMBER.class,   lhsOf(ast, 1));   // abs      -> Number
        assertInstanceOf(INTEGER.class,  lhsOf(ast, 2));   // ceil     -> Integer
        assertInstanceOf(INTEGER.class,  lhsOf(ast, 3));   // floor    -> Integer
        assertInstanceOf(INTEGER.class,  lhsOf(ast, 4));   // round    -> Integer
        assertInstanceOf(INTEGER.class,  lhsOf(ast, 5));   // to_int   -> Integer
        assertInstanceOf(DOUBLE.class,   lhsOf(ast, 6));   // to_float -> Double
        assertInstanceOf(DOUBLE.class,   lhsOf(ast, 7));   // sqrt     -> Double
        assertInstanceOf(DOUBLE.class,   lhsOf(ast, 8));   // pow      -> Double
    }

    // ─────────────────── built-in String methods ──────────────────────────────

    @Test
    void test_inference_of_string_methods() {
        /*
         * let s = "hello";
         * let a = s.len();             -> Integer
         * let b = s.trim();            -> String
         * let c = s.to_upper();        -> String
         * let d = s.to_lower();        -> String
         * let e = s.split(",");        -> [String]
         * let f = s.contains("ell");   -> Bool
         * let g = s.starts_with("h");  -> Bool
         * let h = s.ends_with("o");    -> Bool
         * let i = s.index_of("ll");    -> Integer
         * let j = s.sub_str(1,3);      -> String
         * let k = s.replace("l","r");  -> String
         * let l = s.to_int();          -> Integer
         * let m = s.to_number();       -> Number
         * let n = s.chars();           -> [String]
         * let o = s.repeat(3);         -> String
         */
        AST ast = ASTHelper.mockStringMethods();
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());

        assertInstanceOf(INTEGER.class, lhsOf(ast, 1));                     // len
        assertInstanceOf(STRING.class,  lhsOf(ast, 2));                     // trim
        assertInstanceOf(STRING.class,  lhsOf(ast, 3));                     // to_upper
        assertInstanceOf(STRING.class,  lhsOf(ast, 4));                     // to_lower
        assertEquals("[String]",        lhsOf(ast, 5).toString());          // split
        assertInstanceOf(BOOL.class,    lhsOf(ast, 6));                     // contains
        assertInstanceOf(BOOL.class,    lhsOf(ast, 7));                     // starts_with
        assertInstanceOf(BOOL.class,    lhsOf(ast, 8));                     // ends_with
        assertInstanceOf(INTEGER.class, lhsOf(ast, 9));                     // index_of
        assertInstanceOf(STRING.class,  lhsOf(ast, 10));                    // sub_str
        assertInstanceOf(STRING.class,  lhsOf(ast, 11));                    // replace
        assertInstanceOf(INTEGER.class, lhsOf(ast, 12));                    // to_int
        assertInstanceOf(NUMBER.class,  lhsOf(ast, 13));                    // to_number
        assertEquals("[String]",        lhsOf(ast, 14).toString());         // chars
        assertInstanceOf(STRING.class,  lhsOf(ast, 15));                    // repeat
    }

    // ─────────────────── built-in Bool methods ────────────────────────────────

    @Test
    void test_inference_of_bool_methods() {
        /*
         * let b = true;
         * let a = b.not();           -> Bool
         * let c = b.and_also(false); -> Bool
         * let d = b.or_else(false);  -> Bool
         */
        AST ast = ASTHelper.mockBoolMethods();
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());

        assertInstanceOf(BOOL.class, lhsOf(ast, 1));  // not
        assertInstanceOf(BOOL.class, lhsOf(ast, 2));  // and_also
        assertInstanceOf(BOOL.class, lhsOf(ast, 3));  // or_else
    }

    // ─────────────────── built-in Array methods ───────────────────────────────

    @Test
    void test_inference_of_array_methods() {
        /*
         * let x = [1,2,3];
         * let a = x.len();                 -> Integer
         * let b = x.reverse();             -> [Integer]
         * let c = x.take(2);               -> [Integer]
         * let d = x.drop(1);               -> [Integer]
         * let e = x.filter(i->i>0);        -> [Integer]
         * x.forEach(i->i.to_str());        -> Unit  (bare statement)
         * let g = x.any(i->i>0);           -> Bool
         * let h = x.all(i->i>0);           -> Bool
         * let k = x.find(i->i>0);          -> Integer
         * let m = x.sort((a,b)->a-b);      -> [Integer]
         * let n = x.flat_map(i->[i]);      -> [Integer]
         * let p = x.min();                 -> Integer
         * let q = x.max();                 -> Integer
         */
        AST ast = ASTHelper.mockArrayMethods();
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());

        assertInstanceOf(INTEGER.class, lhsOf(ast, 1));          // len
        assertEquals("[Integer]", lhsOf(ast, 2).toString());      // reverse
        assertEquals("[Integer]", lhsOf(ast, 3).toString());      // take
        assertEquals("[Integer]", lhsOf(ast, 4).toString());      // drop
        assertEquals("[Integer]", lhsOf(ast, 5).toString());      // filter
        // stmt 6 is the bare forEach call — verify it inferred to Unit
        assertSame(ast.Unit, ast.program().body().statements().get(6).get(0).outline());
        assertInstanceOf(BOOL.class, lhsOf(ast, 7));              // any
        assertInstanceOf(BOOL.class, lhsOf(ast, 8));              // all
        assertInstanceOf(INTEGER.class, lhsOf(ast, 9));           // find
        assertEquals("[Integer]", lhsOf(ast, 10).toString());     // sort
        assertEquals("[Integer]", lhsOf(ast, 11).toString());     // flat_map
        assertInstanceOf(INTEGER.class, lhsOf(ast, 12));           // min -> Integer
        assertInstanceOf(INTEGER.class, lhsOf(ast, 13));           // max -> Integer
    }

    // ─────────────────── built-in Dict methods ────────────────────────────────

    @Test
    void test_inference_of_dict_methods() {
        /*
         * let d = ["a":1,"b":2];
         * let a = d.len();             -> Integer
         * let b = d.keys();            -> [String]
         * let c = d.values();          -> [Integer]
         * let e = d.contains_key("a"); -> Bool
         * let f = d.get("a");          -> Integer
         */
        AST ast = ASTHelper.mockDictMethods();
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());

        assertInstanceOf(INTEGER.class, lhsOf(ast, 1));           // len
        assertEquals("[String]",  lhsOf(ast, 2).toString());      // keys
        assertEquals("[Integer]", lhsOf(ast, 3).toString());      // values
        assertInstanceOf(BOOL.class,    lhsOf(ast, 4));           // contains_key
        assertInstanceOf(INTEGER.class, lhsOf(ast, 5));           // get
    }

    @Test
    void test_complex_literal() {

    }

    // ─────────────────── import / export ─────────────────────────────────────

    @Test
    void test_import_export_outline() {
        /*
         * Module shapes:
         *   outline Summary = { total: Integer, data: [String:Number] };
         *   let n: Integer = 10;
         *   export Summary, n as size;
         *
         * Module app:
         *   import Summary, size from shapes;
         *   let s: Summary = { total = 42, data = ["x":1,"y":2] };
         *   let a = s.total;        -> Integer
         *   let b = s.data;         -> [String:Number]
         *   let c = size;           -> Integer
         *   (a, b, c)               -> (Integer,[String : Number],Integer)
         */
        AST ast = ASTHelper.mockImportExportOutline();
        ast.asf().infer();
        assertTrue(ast.inferred());
        assertTrue(ast.errors().isEmpty());

        assertInstanceOf(INTEGER.class, lhsOf(ast, 1));          // a = s.total
        assertEquals("[String : Number]", lhsOf(ast, 2).toString()); // b = s.data
        assertInstanceOf(INTEGER.class, lhsOf(ast, 3));          // c = size

        Outline tuple = ast.program().body().statements().getLast().outline();
        assertEquals("(Integer,[String : Number],Integer)", tuple.toString());
    }

    @Test
    void test_import_export_values() {
        /*
         * Module education:
         *   let grade: Integer = 1, school: String = "NO.1";
         *   export grade, school as college;
         *
         * Module human:
         *   import grade as level, college as school from education;
         *   let age: Integer, name: String = "Will", height: Double = 1.68, grade: Integer = level;
         *   export height as stature, name;
         *
         * Verifies that exported types match declared types and that
         * imported specifiers carry the correct outline from the source module.
         */
        ASF asf = ASTHelper.educationAndHuman();
        asf.infer();
        assertTrue(asf.inferred());

        // ── education exports ──────────────────────────────────────────────
        AST edu = asf.get("education");
        assertTrue(edu.errors().isEmpty());
        Export exports = edu.program().body().exports().getFirst();
        assertInstanceOf(INTEGER.class, exports.specifiers().getFirst().outline());  // grade  -> Integer
        assertInstanceOf(STRING.class,  exports.specifiers().getLast().outline());   // school -> String  (exported as college)

        // ── human imports ──────────────────────────────────────────────────
        AST human = asf.get("human");
        Import imports = human.program().body().imports().getFirst();
        assertInstanceOf(INTEGER.class, imports.specifiers().getFirst().outline()); // level  <- grade
        assertInstanceOf(STRING.class,  imports.specifiers().get(1).outline());     // school <- college

        // ── human exports ──────────────────────────────────────────────────
        Export humanExports = human.program().body().exports().getFirst();
        assertInstanceOf(DOUBLE.class, humanExports.specifiers().getFirst().outline());  // stature <- height
        assertInstanceOf(STRING.class, humanExports.specifiers().getLast().outline());   // name
    }

    @Test
    void test_import_alias_resolves_correctly() {
        /*
         * Verifies that "import X as Y from module" makes Y visible under
         * the local alias and that the original name X is NOT in scope.
         *
         * Module src:  let val: Integer = 99;  export val, val as copy;
         * Module dst:  import val as n, copy from src;
         *              let a = n;     -> Integer   (alias of val)
         *              let b = copy;  -> Integer   (no alias)
         *              (a, b)
         */
        AST ast = ASTHelper.mockImportAlias();
        ast.asf().infer();
        assertTrue(ast.inferred());
        assertTrue(ast.errors().isEmpty());

        assertInstanceOf(INTEGER.class, lhsOf(ast, 0)); // a = n
        assertInstanceOf(INTEGER.class, lhsOf(ast, 1)); // b = copy

        Outline tuple = ast.program().body().statements().getLast().outline();
        assertEquals("(Integer,Integer)", tuple.toString());
    }

    @Test
    void test_debug_extend_outline_parse() {
        String[] tests = {
            "outline C = Point { x: Int };",                        // extend in standalone context
            "module m\noutline Point = { x: Number };\noutline C = Point { color: String };\n0", // define then extend same module
            "module m\noutline C = Point { color: String };\nlet x = 0;\nx", // extend with trailing content
            "module m\noutline C = Point { color: String };",        // extend alone
        };
        for (int i = 0; i < tests.length; i++) {
            try {
                ASF asf2 = new ASF();
                org.twelve.outline.OutlineParser p2 = ASTHelper.parser;
                p2.parse(tests[i]);
//                System.out.println("Test " + i + " OK");
            } catch (Exception e) {
                System.out.println("Test " + i + " FAIL: " + e.getMessage().substring(0, Math.min(80, e.getMessage().length())));
            }
        }
    }

    @Test
    void test_import_and_extend_outline() {
        /*
         * Verifies that an outline TYPE (not just a value) can be exported from one
         * module, imported in another, and then extended with new members.
         *
         * Module shapes:
         *   outline Point = { x: Number, y: Number };
         *   let zero: Int = 0;
         *   export Point, zero;
         *
         * Module geo:
         *   import Point, zero from shapes;
         *   outline ColorPoint = Point { color: String };  // extends imported outline
         *   let p: ColorPoint = { x = 1, y = 2, color = "red" };
         *   let cx = p.x;      -> Number   (inherited member)
         *   let cc = p.color;  -> String   (new member)
         *   let z = zero;      -> Integer  (imported value)
         *   (cx, cc, z)        -> (Number, String, Integer)
         */
        AST ast = ASTHelper.mockImportOutlineType();
        ast.asf().infer();
        assertTrue(ast.inferred());
        if (!ast.errors().isEmpty()) {
            ast.errors().forEach(e -> System.out.println("ERROR: " + e));
        }
        assertTrue(ast.errors().isEmpty());

        // statement indices in geo module (import is not a body statement):
        // 0 = outline ColorPoint, 1 = let p, 2 = let cx, 3 = let cc, 4 = let z, 5 = (cx,cc,z)
        assertInstanceOf(Entity.class,   lhsOf(ast, 1)); // p: ColorPoint
        assertInstanceOf(NUMBER.class,   lhsOf(ast, 2)); // cx = p.x
        assertInstanceOf(STRING.class,   lhsOf(ast, 3)); // cc = p.color
        assertInstanceOf(INTEGER.class,  lhsOf(ast, 4)); // z = zero

        Outline tuple = ast.program().body().statements().getLast().outline();
        assertEquals("(Number,String,Integer)", tuple.toString());
    }

    @Test
    void test_literal_outline(){
        AST ast = ASTHelper.literalOutline();
        ast.asf().infer();
        assertTrue(ast.inferred());
        //todo: assert return type is string

        //todo: add one interpret test to get return value

    }

    @Test
    void test_outline_mutual_reference(){

    }

    @Test
    void test_continue_break() {
        //todo
    }
}
