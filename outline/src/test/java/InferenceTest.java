import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.exception.GCPErrCode;
import org.twelve.gcp.exception.GCPError;
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
import org.twelve.gcp.outline.primitive.FLOAT;
import org.twelve.gcp.outline.primitive.INTEGER;
import org.twelve.gcp.outline.primitive.NUMBER;
import org.twelve.gcp.outline.primitive.STRING;
import org.twelve.gcp.outline.primitive.SYMBOL;
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
    void test_nested_entity(){
        AST ast = ASTHelper.mockNestedEntity();
        assertTrue(ast.asf().infer());
        assertTrue(ast.asf().inferred());
        assertTrue(ast.errors().isEmpty());
        assertInstanceOf(STRING.class, ast.program().body().statements().getLast().outline());
    }

    @Test
    void test_nested_entity_method_can_capture_outer_recursive_binding() {
        AST ast = ASTHelper.mockRecursiveNestedEntityClosure();
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());

        VariableDeclarator declarator = cast(ast.program().body().statements().getFirst());
        Entity male = cast(declarator.assignments().getFirst().lhs().outline());
        Entity wife = cast(male.getMember("wife").orElseThrow().outline());
        Function<?, ?> husband = cast(wife.getMember("husband").orElseThrow().outline().eventual());

        assertInstanceOf(Entity.class, husband.returns().supposedToBe().eventual());
        assertTrue(husband.returns().supposedToBe().eventual().is(male));
        assertTrue(male.is(husband.returns().supposedToBe().eventual()));
        assertInstanceOf(Entity.class, ast.program().body().statements().getLast().outline().eventual());
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
        // person.members() = [name(phase-1), get_name(phase-2), get_my_name(phase-2)]
        // interact(own=[get_name(override), get_other_name], base=[name, get_name, get_my_name]):
        //   → [get_other_name, name, get_name(Poly), get_my_name]
        assertEquals(4, members.size());
        // index 1: name field is STRING
        assertInstanceOf(STRING.class, members.get(1).outline());
        // index 2: get_name is Poly (original ()->String  +  override last_name->{100})
        assertInstanceOf(Poly.class, members.get(2).outline());
        Poly getName = cast(members.get(2).outline());
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
        let f = fn<a,b>(x:a)->{
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
        let g = fn<a,b>()->{
           {
                z:a = 100,
                f = fn<c>(x:b,y:c)->y
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
        assertInstanceOf(Option.class, name1Outline);
        assertTrue(((Option) name1Outline).options().stream().anyMatch(o -> o instanceof STRING));
        assertTrue(((Option) name1Outline).options().stream().anyMatch(o -> o == ast.Nothing));
        assertEquals("String|(Integer,String)", name2Outline.toString());
        assertEquals(2, ast.errors().size());
        assertTrue(ast.errors().stream().allMatch(e -> e.errorCode() == GCPErrCode.NON_EXHAUSTIVE_MATCH));
        assertTrue(ast.errors().stream().anyMatch(e -> e.node() == name1.assignments().getFirst().rhs()));
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
    void test_non_exhaustive_match_as_function_return_becomes_nothing_option() {
        AST ast = ASTHelper.mockNonExhaustiveMatchAsFunctionReturn();
        assertTrue(ast.asf().infer());
        List<Statement> statements = ast.program().body().statements();
        VariableDeclarator result = cast(statements.get(statements.size() - 1));
        Outline resultOutline = result.assignments().getFirst().lhs().outline();
        assertInstanceOf(Option.class, resultOutline);
        assertTrue(((Option) resultOutline).options().stream().anyMatch(o -> o instanceof STRING));
        assertTrue(((Option) resultOutline).options().stream().anyMatch(o -> o == ast.Nothing));
        assertTrue(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.NON_EXHAUSTIVE_MATCH));
        assertFalse(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.AMBIGUOUS_RETURN));
    }

    @Test
    void test_non_exhaustive_selection_before_tail_keeps_following_code_reachable() {
        AST ast = ASTHelper.mockNonExhaustiveSelectionBeforeTail();
        assertTrue(ast.asf().infer());
        List<Statement> statements = ast.program().body().statements();
        VariableDeclarator result = cast(statements.get(statements.size() - 1));
        Outline resultOutline = result.assignments().getFirst().lhs().outline();
        assertEquals("Integer|String", resultOutline.toString());
        assertFalse(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.UNREACHABLE_STATEMENT));
        assertFalse(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.AMBIGUOUS_RETURN));
    }

    @Test
    void test_exhaustive_selection_before_tail_marks_following_code_unreachable() {
        AST ast = ASTHelper.mockExhaustiveSelectionBeforeTail();
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.UNREACHABLE_STATEMENT));
        assertFalse(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.AMBIGUOUS_RETURN));
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
    @Disabled("known-incomplete: forward reference from outline body not fully resolved by inferer")
    void test_future_reference_from_outline(){
        AST ast = ASTHelper.mockFutureReferenceFromOutline();
        ast.asf().infer();
        assertTrue(ast.inferred());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("(Integer,[String : Number],Integer)",outline.toString());
    }

    @Test
    @Disabled("known-incomplete: cross-module invoke chains do not converge to fully-resolved types")
    void test_inter_invoke(){
        AST ast = ASTHelper.mockInterInvoke();
        ast.asf().infer();
        assertTrue(ast.inferred());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("(Asia,String,Integer,Asia,String,String,A|B|C,A|B|C)",outline.toString());
    }

    @Test
    @Disabled("known-incomplete: Lazy reference-call constraint propagation incomplete")
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

    // ─────────────────── forward-reference constraint propagation ─────────────

    /**
     * Reproduces: f uses g before g is declared; g's return type is constrained
     * by the arithmetic in f (g(x.son) - 1 demands Number), but g = y -> y.age
     * resolves to String when the call site provides age="will".
     *
     * Expected behaviour: the final call should produce at least one type-mismatch
     * or NOT_A_FUNCTION / OUTLINE_MISMATCH error because String cannot participate
     * in numeric subtraction.
     *
     * Current behaviour (bug): no errors are reported — the mismatch is silently
     * swallowed because the backward constraint from "- 1" is not propagated back
     * through the forward reference to g.
     */
    @Test
    void forward_ref_arithmetic_constraint_mismatch_should_report_error() {
        AST ast = ASTHelper.parser.parse(new ASF(), """
            let f = x -> g(x.son) - 1;
            let g = y -> y.age;
            f({son = {age = "will"}})
            """);
        ast.asf().infer();
        // The call resolves g's return to String (from age="will"),
        // which then feeds into "- 1" — that must be a type error.
        assertFalse(ast.errors().isEmpty(),
            "Expected at least one type error: String cannot be used in arithmetic subtraction");
    }

    /**
     * Sanity check: when the field is numeric the exact same program should be error-free.
     */
    @Test
    void forward_ref_arithmetic_constraint_with_numeric_field_is_ok() {
        AST ast = ASTHelper.parser.parse(new ASF(), """
            let f = x -> g(x.son) - 1;
            let g = y -> y.age;
            f({son = {age = 30}})
            """);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty(),
            "No type error expected: age=30 (Integer), g returns Integer, Integer-1 is valid");
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
         * x.each(i->i.to_str());           -> Unit  (bare statement)
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
        ast.errors().forEach(e -> System.out.println("[DBG-ARR] " + e.errorCode() + ": " + e.message() + " node=" + e.node()));
        assertTrue(ast.errors().isEmpty());

        assertInstanceOf(INTEGER.class, lhsOf(ast, 1));          // len
        assertEquals("[Integer]", lhsOf(ast, 2).toString());      // reverse
        assertEquals("[Integer]", lhsOf(ast, 3).toString());      // take
        assertEquals("[Integer]", lhsOf(ast, 4).toString());      // drop
        assertEquals("[Integer]", lhsOf(ast, 5).toString());      // filter
        // stmt 6 is the bare each call — verify it inferred to Unit
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
        AST ast = ASTHelper.mockComplexLiteral();
        assertTrue(ast.asf().infer());
        assertFalse(ast.errors().isEmpty());//literal outline: issuer can't be assigned
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
        AST ast = ASTHelper.mockLiteralOutline();
        ast.asf().infer();
        assertTrue(ast.inferred());
        // diagnostic: show error detail
        ast.errors().forEach(e -> {
            System.out.println("ERROR node type: " + e.node().getClass().getSimpleName() + ", outline: " + e.node().outline() + ", parent: " + e.node().parent().getClass().getSimpleName());
            if (e.node().outline() instanceof org.twelve.gcp.outline.adt.Entity entity) {
                entity.members().stream().filter(m -> !m.isDefault()).forEach(m -> 
                    System.out.println("  member: " + m.name() + " -> " + m.outline().getClass().getSimpleName() + " = " + m.outline()));
            }
        });
        assertTrue(ast.errors().isEmpty(), "expected no errors but got: " + ast.errors());

        // person.specie should infer to Literal("human", STRING)
        int n = ast.program().body().statements().size();
        org.twelve.gcp.ast.Node specieAccess = ast.program().body().statements().get(n - 1).get(0);
        assertInstanceOf(org.twelve.gcp.outline.primitive.Literal.class, specieAccess.outline());
        org.twelve.gcp.outline.primitive.Literal lit = cast(specieAccess.outline());
        assertInstanceOf(STRING.class, lit.outline()); // origin type is String

        // execution: person.specie should evaluate to "human"
        org.twelve.gcp.interpreter.value.Value result = ast.asf().interpret();
        assertInstanceOf(org.twelve.gcp.interpreter.value.StringValue.class, result);
        assertEquals("human", ((org.twelve.gcp.interpreter.value.StringValue) result).value());
    }

    @Test
    void test_declared_match(){
        AST ast = ASTHelper.mockDeclaredMatch();
        ast.asf().infer();
        assertTrue(ast.inferred());
        assertFalse(ast.errors().stream().anyMatch(e -> e.severity() == GCPError.Severity.ERROR), "" + ast.errors());
    }

    @Test
    void test_more_option(){
        AST ast = ASTHelper.mockMoreOption();
        ast.asf().infer();
        assertTrue(ast.inferred());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("(String|Integer|Nothing,String|Integer|Nothing)", outline.toString());
    }

    @Test
    @Disabled("known-incomplete: recursive `outline X = ... X ...` extension not yet inferred end-to-end")
    void test_recursive_extension(){
        AST ast = ASTHelper.mockRecurExtend();
        ast.asf().infer();
        System.out.println("[TEST] errors=" + ast.errors());
        var stmts = ast.program().body().statements();
        // base, mapped, filtered are at indices 2,3,4
        System.out.println("[TEST] base=" + stmts.get(2).get(0).get(0).outline());
        System.out.println("[TEST] mapped=" + stmts.get(3).get(0).get(0).outline());
        System.out.println("[TEST] filtered=" + stmts.get(4).get(0).get(0).outline());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("[Integer]", outline.toString());
        //todo: 添加在InterpretTest中添加interpret测试，断言输出结果
    }

    @Test
    void test_built_in_entity() throws Exception {
        // ── math: constant fields and pure functions ─────────────────────────
        AST astMath = RunnerHelper.parse("""
                let pi     = math.pi;
                let e      = math.e;
                let r      = math.sqrt(4.0);
                let fl     = math.floor(3.7);
                let ce     = math.ceil(3.1);
                let ro     = math.round(3.5);
                let mx     = math.max(3)(7);
                let mn     = math.min(3)(7);
                let pw     = math.pow(2.0)(10.0);
                let rnd    = math.random();
                """);
        assertTrue(astMath.asf().infer(), "math inference failed");
        assertTrue(astMath.errors().isEmpty(), "math errors: " + astMath.errors());

        Outline pi  = lhsOf(astMath, 0);
        Outline e   = lhsOf(astMath, 1);
        Outline sqr = lhsOf(astMath, 2);
        Outline fl  = lhsOf(astMath, 3);
        Outline ce  = lhsOf(astMath, 4);
        Outline ro  = lhsOf(astMath, 5);
        Outline mx  = lhsOf(astMath, 6);
        Outline mn  = lhsOf(astMath, 7);
        Outline pw  = lhsOf(astMath, 8);
        Outline rnd = lhsOf(astMath, 9);

        assertInstanceOf(org.twelve.gcp.outline.primitive.DOUBLE.class, pi,  "math.pi should be Double");
        assertInstanceOf(org.twelve.gcp.outline.primitive.DOUBLE.class, e,   "math.e should be Double");
        assertInstanceOf(org.twelve.gcp.outline.primitive.DOUBLE.class, sqr, "math.sqrt → Double");
        assertInstanceOf(INTEGER.class,                                   fl,  "math.floor → Int");
        assertInstanceOf(INTEGER.class,                                   ce,  "math.ceil → Int");
        assertInstanceOf(INTEGER.class,                                   ro,  "math.round → Int");
        assertInstanceOf(NUMBER.class,                                    mx,  "math.max → Number");
        assertInstanceOf(NUMBER.class,                                    mn,  "math.min → Number");
        assertInstanceOf(org.twelve.gcp.outline.primitive.DOUBLE.class, pw,  "math.pow → Double");
        assertInstanceOf(org.twelve.gcp.outline.primitive.DOUBLE.class, rnd, "math.random → Double");

        // ── console: access method types via the module entity ───────────────
        // console.log is a Any→Unit function; console.read is a Unit→String function.
        // We verify by binding the function itself (not calling it).
        AST astConsole = RunnerHelper.parse("""
                let log_fn  = console.log;
                let warn_fn = console.warn;
                let err_fn  = console.error;
                let read_fn = console.read;
                """);
        assertTrue(astConsole.asf().infer(), "console inference failed");
        assertTrue(astConsole.errors().isEmpty(), "console errors: " + astConsole.errors());

        // log_fn : Any → Unit  →  FirstOrderFunction
        assertInstanceOf(org.twelve.gcp.outline.projectable.FirstOrderFunction.class,
                lhsOf(astConsole, 0), "console.log → FirstOrderFunction");
        // warn_fn: Any → Unit
        assertInstanceOf(org.twelve.gcp.outline.projectable.FirstOrderFunction.class,
                lhsOf(astConsole, 1), "console.warn → FirstOrderFunction");
        // err_fn: Any → Unit
        assertInstanceOf(org.twelve.gcp.outline.projectable.FirstOrderFunction.class,
                lhsOf(astConsole, 2), "console.error → FirstOrderFunction");
        // read_fn: Unit → String  →  FirstOrderFunction
        assertInstanceOf(org.twelve.gcp.outline.projectable.FirstOrderFunction.class,
                lhsOf(astConsole, 3), "console.read → FirstOrderFunction");

        // ── date: now() returns an entity with year:Int, format:String→String ─
        AST astDate = RunnerHelper.parse("""
                let d      = date.now();
                let yr     = date.now().year;
                let mo     = date.now().month;
                let day    = date.now().day;
                let fmt    = date.now().format("YYYY-MM-DD");
                let ts     = date.now().timestamp();
                let dow    = date.now().day_of_week();
                let parsed = date.parse("2025-06-15");
                let pyr    = date.parse("2025-06-15").year;
                """);
        assertTrue(astDate.asf().infer(), "date inference failed");
        assertTrue(astDate.errors().isEmpty(), "date errors: " + astDate.errors());

        assertInstanceOf(Entity.class,  lhsOf(astDate, 0), "date.now() → Entity (DateRecord)");
        assertInstanceOf(INTEGER.class, lhsOf(astDate, 1), "date.now().year → Int");
        assertInstanceOf(INTEGER.class, lhsOf(astDate, 2), "date.now().month → Int");
        assertInstanceOf(INTEGER.class, lhsOf(astDate, 3), "date.now().day → Int");
        assertInstanceOf(STRING.class,  lhsOf(astDate, 4), "date.now().format() → String");
        assertInstanceOf(org.twelve.gcp.outline.primitive.LONG.class,
                                        lhsOf(astDate, 5), "date.now().timestamp() → Long");
        assertInstanceOf(INTEGER.class, lhsOf(astDate, 6), "date.now().day_of_week() → Int");
        assertInstanceOf(Entity.class,  lhsOf(astDate, 7), "date.parse() → Entity (DateRecord)");
        assertInstanceOf(INTEGER.class, lhsOf(astDate, 8), "date.parse().year → Int");
    }

    /**
     * outline ApiKey = { key: String, alias: "alice", access: String, issuer: #"GCP-System" };
     *
     * Verifies:
     * 1. key1 = ApiKey{key="a", access="b"} – no errors, anonymous structural type
     * 2. key2 = ApiKey{alias="jack", age="c"} – 1 error (key+access combined into one message)
     * 3. key3 = ApiKey{key="key"} – 1 error (access missing)
     * 4. key4 = ApiKey1{key="key", access="admin"} – ApiKey1 auto-symbol, type is SymbolEntity
     * 5. key1.issuer runtime → "GCP-System"
     */
    @Test
    void test_outline_entity_template() {
        AST ast = ASTHelper.mockOutlineEntityTemplate();
        ast.asf().infer();

        // AST deduplicates errors by (node, errorCode), so multiple missing fields on the same
        // entity construction are reported as one combined error per construction.
        // key2: 'key','access' combined → 1 error; key3: 'access' → 1 error → total 2
        long missingFieldErrors = ast.errors().stream()
                .filter(e -> e.errorCode() == org.twelve.gcp.exception.GCPErrCode.MISSING_REQUIRED_FIELD)
                .count();
        assertEquals(2, missingFieldErrors,
                "expected 2 MISSING_REQUIRED_FIELD errors (1 combined for key2, 1 for key3)");

        // key1 should be an anonymous Entity (base == ANY, not SymbolEntity)
        org.twelve.gcp.ast.Node key1Node = ast.program().body().statements().get(1)
                .nodes().getFirst().nodes().getFirst();
        org.twelve.gcp.outline.Outline key1Type = key1Node.outline();
        assertInstanceOf(org.twelve.gcp.outline.adt.Entity.class, key1Type);
        assertFalse(key1Type instanceof org.twelve.gcp.outline.adt.SymbolEntity,
                "key1 should be anonymous entity, not SymbolEntity");

        // key1 type should have 4 non-default members: key, alias, access, issuer
        org.twelve.gcp.outline.adt.Entity key1Entity = cast(key1Type);
        List<org.twelve.gcp.outline.adt.EntityMember> key1Members =
                key1Entity.members().stream().filter(m -> !m.isDefault()).toList();
        assertEquals(4, key1Members.size(),
                "key1 should have key, alias, access, issuer (4 members)");

        // key4 should be a SymbolEntity with ApiKey1 tag
        org.twelve.gcp.ast.Node key4Node = ast.program().body().statements().get(4)
                .nodes().getFirst().nodes().getFirst();
        assertInstanceOf(org.twelve.gcp.outline.adt.SymbolEntity.class, key4Node.outline(),
                "key4 should be a SymbolEntity (ApiKey1 is undefined → auto-symbol)");

        // Runtime: key1.issuer should return "GCP-System"
        org.twelve.gcp.interpreter.value.Value result = ast.asf().interpret();
        assertInstanceOf(org.twelve.gcp.interpreter.value.StringValue.class, result);
        assertEquals("GCP-System",
                ((org.twelve.gcp.interpreter.value.StringValue) result).value());
    }

    @Test
    void test_missing_required_field_points_to_construction_site() {
        AST ast = ASTHelper.parser.parse(new ASF(), """
                // outline Product has:
                //   id, price   -> REQUIRED
                //   category    -> DEFAULT VALUE ("general")
                //   sku         -> LITERAL TYPE (#"AUTO")

                outline Product = {
                    id:       String,
                    price:    Int,
                    category: "general",
                    sku:      #"AUTO"
                };
                let p3 = Product{id = "P003"};
                """);
        ast.asf().infer();

        var missing = ast.errors().stream()
                .filter(e -> e.errorCode() == GCPErrCode.MISSING_REQUIRED_FIELD)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a missing required field error"));

        assertNotNull(missing.node(), "missing-field error should keep a source node");
        assertTrue(missing.node().loc().start() >= 0, "missing-field error should keep a real source offset");
        assertEquals(12, missing.node().loc().line(), "error should point to the Product{...} call site");
    }

    /**
     * outline Config = { host: String, env: "prod", tag: #"v1" };
     * let cfg = Config{host="localhost"};
     * cfg.env   → "prod"   (default-value field filled at runtime)
     */
    @Test
    void test_outline_entity_default_fill() {
        AST ast = ASTHelper.mockOutlineEntityDefaultFill();
        ast.asf().infer();

        // Only 1 error expected: access is missing  – wait, Config has host (required), env (default), tag (literal)
        // key3 = Config{host="localhost"} → host provided, env defaults to "prod", tag="#v1" → no errors
        assertTrue(ast.errors().isEmpty(),
                "Config{host='localhost'} should have no errors; got: " + ast.errors());

        // Runtime: cfg.env should return "prod"
        org.twelve.gcp.interpreter.value.Value result = ast.asf().interpret();
        assertInstanceOf(org.twelve.gcp.interpreter.value.StringValue.class, result);
        assertEquals("prod",
                ((org.twelve.gcp.interpreter.value.StringValue) result).value());
    }

    /**
     * outline Human = { speed: Int, run: ()->this.speed }
     * run is a default method — inferred as () → Int, no errors, callable.
     */
    @Test
    void test_outline_default_method() {
        AST ast = ASTHelper.mockOutlineMethod();
        assertTrue(ast.asf().infer(), "inference should succeed");
        assertTrue(ast.errors().isEmpty(),
                "no errors expected for outline default method; got: " + ast.errors());
        org.twelve.gcp.outline.Outline result = ast.program().body().statements().getLast().outline();
        assertInstanceOf(INTEGER.class, result,
                "h.run() should infer Int; got: " + result);
    }

    /**
     * outline Human = { speed: Int, run: #()->this.speed }
     * run is a literal (sealed) method — no errors, callable.
     */
    @Test
    void test_outline_literal_method() {
        AST ast = ASTHelper.mockOutlineMethodLiteral();
        assertTrue(ast.asf().infer(), "inference should succeed");
        assertTrue(ast.errors().isEmpty(),
                "no errors expected for outline literal method; got: " + ast.errors());
        org.twelve.gcp.outline.Outline result = ast.program().body().statements().getLast().outline();
        assertInstanceOf(INTEGER.class, result,
                "h.run() should infer Int; got: " + result);
    }

    /**
     * outline Gender = Male | Female; outline Man = Human{ age: Int, gender: #Male }.
     * gender is a Symbol literal type — no errors, infers as SYMBOL, NOT_ASSIGNABLE if overridden.
     */
    @Test
    void test_symbol_literal_type() {
        AST ast = ASTHelper.mockSymbolLiteralType();
        assertTrue(ast.asf().infer(), "inference should succeed");
        assertTrue(ast.errors().isEmpty(),
                "no errors expected for symbol literal type; got: " + ast.errors());
        org.twelve.gcp.outline.Outline result = ast.program().body().statements().getLast().outline();
        assertInstanceOf(org.twelve.gcp.outline.primitive.Literal.class, result,
                "man.gender should infer as Literal(Male); got: " + result);
        assertInstanceOf(SYMBOL.class, ((org.twelve.gcp.outline.primitive.Literal) result).outline(),
                "Literal should wrap SYMBOL; got: " + ((org.twelve.gcp.outline.primitive.Literal) result).outline());
    }

    /**
     * outline Service = { name: String, meta: #{ env: "prod", version: 1 } };
     * let s = Service{name="api", meta={env="dev",version=2}};
     * Expects 1 NOT_ASSIGNABLE error on 'meta' (entity literal type is immutable).
     */
    @Test
    void test_entity_literal_type() {
        AST ast = ASTHelper.mockEntityLiteralType();
        assertTrue(ast.asf().infer());
        long notAssignable = ast.errors().stream()
                .filter(e -> e.errorCode() == GCPErrCode.NOT_ASSIGNABLE)
                .count();
        assertEquals(1, notAssignable,
                "expected 1 NOT_ASSIGNABLE error for meta (entity literal); got: " + ast.errors());
    }

    /**
     * outline Origin = { label: String, coords: #(0, 0) };
     * let o = Origin{label="center", coords=(1,2)};
     * Expects 1 NOT_ASSIGNABLE error on 'coords' (tuple literal type is immutable).
     */
    @Test
    void test_tuple_literal_type() {
        AST ast = ASTHelper.mockTupleLiteralType();
        assertTrue(ast.asf().infer());
        long notAssignable = ast.errors().stream()
                .filter(e -> e.errorCode() == GCPErrCode.NOT_ASSIGNABLE)
                .count();
        assertEquals(1, notAssignable,
                "expected 1 NOT_ASSIGNABLE error for coords (tuple literal); got: " + ast.errors());
    }

    @Test
    void test_inherited_this() {
        AST ast = ASTHelper.mockInheritedThis();
        assertTrue(ast.asf().infer());
        assertEquals(1, ast.errors().size());
        assertEquals(GCPErrCode.NOT_ASSIGNABLE, ast.errors().getFirst().errorCode());
        assertEquals("age", ast.errors().getFirst().node().lexeme());
        // Runtime
        org.twelve.gcp.interpreter.value.Value result = ast.asf().interpret();
        assertInstanceOf(org.twelve.gcp.interpreter.value.IntValue.class, result);
        assertEquals(80,
                ((org.twelve.gcp.interpreter.value.IntValue) result).value());
    }

    @Test
    void test_let_reassign_error() {
        // let a = 100; a = 200  →  NOT_ASSIGNABLE on 'a'
        AST ast = RunnerHelper.parse("let a = 100; a = 200;");
        ast.asf().infer();
        assertEquals(1, ast.errors().size());
        assertEquals(GCPErrCode.NOT_ASSIGNABLE, ast.errors().getFirst().errorCode());
        assertEquals("a", ast.errors().getFirst().node().lexeme());
    }

    @Test
    void test_var_reassign_ok() {
        // var a = 100; a = 200  →  no error
        AST ast = RunnerHelper.parse("var a = 100; a = 200;");
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty());
    }

    @Test
    void test_protected_member_external_access_error() {
        // let a = {_age = 100}; a._age  →  NOT_ACCESSIBLE on '_age'
        AST ast = RunnerHelper.parse("let a = {_age = 100}; a._age;");
        ast.asf().infer();
        assertEquals(1, ast.errors().size());
        assertEquals(GCPErrCode.NOT_ACCESSIBLE, ast.errors().getFirst().errorCode());
        assertEquals("_age", ast.errors().getFirst().node().lexeme());
    }

    @Test
    void test_protected_member_internal_access_ok() {
        // _-prefixed members are accessible via this inside the entity
        AST ast = RunnerHelper.parse("""
                let a = {
                    _age = 100,
                    getAge = ()->this._age
                };
                a.getAge();
                """);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty());
    }

    @Test
    void test_outline_type_member_access_error() {
        // Human.gender where Human is a type definition should report OUTLINE_USED_AS_VALUE
        AST ast = RunnerHelper.parse("""
                outline Gender = Male|Female;
                outline Human = {
                    gender:Gender
                };
                let a = Human.gender;
                """);
        ast.asf().infer();
        assertFalse(ast.errors().isEmpty());
        assertEquals(GCPErrCode.OUTLINE_USED_AS_VALUE, ast.errors().getFirst().errorCode());
        assertEquals("Human", ast.errors().getFirst().node().lexeme());
    }

    @Test
    void test_outline_instance_member_access_ok() {
        // Instance member access must still work
        AST ast = RunnerHelper.parse("""
                outline Gender = Male|Female;
                outline Human = {
                    gender:Gender
                };
                let man = Human{gender=Male};
                man.gender;
                """);
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty());
    }

    /**
     * Entity literal members declared with 'let' are immutable and cannot be reassigned
     * via member-accessor assignment from outside the entity.
     * Members declared with 'var' are mutable and can be reassigned.
     *
     *   let base = { var height = 100, let label = "hello" };
     *   base.label  = "world";  // NOT ALLOWED – let member
     *   base.height = 200;      // ALLOWED   – var member
     */
    @Test
    void test_entity_member_let_not_assignable() {
        AST ast = RunnerHelper.parse("""
                let base = { var height = 100, let label = "hello" };
                base.label = "world";
                base.height = 200;
                """);
        ast.asf().infer();
        assertEquals(1, ast.errors().size());
        assertEquals(GCPErrCode.NOT_ASSIGNABLE, ast.errors().getFirst().errorCode());
        assertEquals("label", ast.errors().getFirst().node().lexeme());
    }

    /**
     * var-declared entity members can be reassigned; let-declared cannot.
     * Tests the full scenario from the spec:
     *
     *   let base = {
     *       var _age = 10,
     *       let get_age = ()->this._age,
     *       var height = 100
     *   };
     *   base.get_age = ()->0;  // NOT ALLOWED
     *   base.height  = 200;    // ALLOWED
     */
    @Test
    void test_entity_member_var_let_mixed() {
        AST ast = RunnerHelper.parse("""
                let base = {
                    var _age = 10,
                    let get_age = ()->this._age,
                    var height = 100
                };
                base.get_age = ()->0;
                base.height = 200;
                """);
        ast.asf().infer();
        assertEquals(1, ast.errors().size());
        assertEquals(GCPErrCode.NOT_ASSIGNABLE, ast.errors().getFirst().errorCode());
        assertEquals("get_age", ast.errors().getFirst().node().lexeme());
    }

    @Test
    void test_negative_number() {
        AST ast = RunnerHelper.parse("let a = -1;");
        ast.asf().infer();
        assertEquals(0, ast.errors().size());
    }

    @Test
    void test_long_literal() {
        AST ast = RunnerHelper.parse("let a:Long = 1L;");
        ast.asf().infer();
        assertEquals(0, ast.errors().size());
    }

    @Test
    void test_long_literal_inferred() {
        AST ast = RunnerHelper.parse("let a = 1L;");
        ast.asf().infer();
        assertEquals(0, ast.errors().size());
    }

    @Test
    void test_prefix_increment_ok() {
        AST ast = RunnerHelper.parse("var i = 0; ++i;");
        ast.asf().infer();
        assertEquals(0, ast.errors().size());
    }

    @Test
    void test_postfix_increment_ok() {
        AST ast = RunnerHelper.parse("var i = 0; i++;");
        ast.asf().infer();
        assertEquals(0, ast.errors().size());
    }

    @Test
    void test_prefix_decrement_ok() {
        AST ast = RunnerHelper.parse("var i = 5; --i;");
        ast.asf().infer();
        assertEquals(0, ast.errors().size());
    }

    @Test
    void test_postfix_decrement_ok() {
        AST ast = RunnerHelper.parse("var i = 5; i--;");
        ast.asf().infer();
        assertEquals(0, ast.errors().size());
    }

    @Test
    void test_increment_on_float_is_error() {
        AST ast = RunnerHelper.parse("var f = 1.5; ++f;");
        ast.asf().infer();
        assertEquals(1, ast.errors().size());
    }

    @Test
    void test_random_code_1(){
        AST ast = RunnerHelper.parse("""
                
                let ent = 100;
                let f_ent = (ent)->ent.name;""");
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
    }

    @Test
    void test_outline_numeric_defaults_display() {
        // outline A = { age:100, height:168 } — type should display as {age:Int, height:Int}, not {}
        AST ast = RunnerHelper.parse("""
                outline A = {
                    age:100,
                    height:168
                };
                """);
        assertTrue(ast.asf().infer());
        Outline aOutline = cast(ast.symbolEnv().lookupAll("A").outline());
        String display = aOutline.toString();
        assertTrue(display.contains("age"), "Expected type to contain 'age', got: " + display);
        assertTrue(display.contains("height"), "Expected type to contain 'height', got: " + display);
    }

    @Test
    void test_poly_entity_arg_no_project_fail() {
        // f_ent(value) where value is Poly — should NOT produce a PROJECT_FAIL error
        AST ast = RunnerHelper.parse("""
                let value = 10 & "Will" & {name="Bob"};
                let f_ent = (value:{name:String}) -> value.name;
                let ent = f_ent(value);
                """);
        assertTrue(ast.asf().infer());
        long projectFails = ast.errors().stream()
                .filter(e -> e.errorCode() == GCPErrCode.PROJECT_FAIL).count();
        assertEquals(0, projectFails, "f_ent(value) should not produce PROJECT_FAIL errors");
    }

    @Test
    void test_poly_str_with_extension_no_error() {
        // f_str("aaa"{age=20}) — should work: String extended with {age:Int} satisfies (value:String)->value.age
        AST ast = RunnerHelper.parse("""
                let f_str = (value:String) -> value.age;
                let age_2 = f_str("aaa"{age=20});
                """);
        assertTrue(ast.asf().infer());
        long projectFails = ast.errors().stream()
                .filter(e -> e.errorCode() == GCPErrCode.PROJECT_FAIL).count();
        assertEquals(0, projectFails, "f_str(\"aaa\"{age=20}) should not produce PROJECT_FAIL errors");
    }

    @Test
    void test_poly_str_without_extension_has_error() {
        // f_str(value) where value's String component lacks 'age' — should have an inference error
        AST ast = RunnerHelper.parse("""
                let value = 10 & "Will" & {name="Bob"};
                let f_str = (value:String) -> value.age;
                let age_1 = f_str(value);
                """);
        assertTrue(ast.asf().infer());
        assertFalse(ast.errors().isEmpty(), "f_str(value) should produce an inference error (String has no 'age')");
    }
    @Test
    void test_poly_assignment() {
        AST ast = RunnerHelper.parse("""
                var value = 10 & "Will" & {name="Bob"};
                value = 100;
                value
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("Integer&String&{name: String}",outline.toString());
    }

    @Test
    void test_poly_partial_poly_assignment_no_error() {
        AST ast = RunnerHelper.parse("""
                var value = 10 & "Will" & {name="Bob"};
                value = 200 & "Will1";
                value
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("Integer&String&{name: String}", outline.toString());
    }

    @Test
    void test_poly_full_poly_assignment_no_error() {
        AST ast = RunnerHelper.parse("""
                var value = 10 & "Will" & {name="Bob"};
                value = 200 & "Will1" & {name="Bob2"};
                value
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("Integer&String&{name: String}", outline.toString());
    }

    @Test
    void test_poly_assignment_with_unmatched_type_has_error() {
        AST ast = RunnerHelper.parse("""
                var value = 10 & "Will" & {name="Bob"};
                value = 200 & true;
                value
                """);
        ast.asf().infer();
        assertFalse(ast.errors().isEmpty());
    }

    @Test
    void test_option_in_value_expression_is_invalid() {
        AST ast = RunnerHelper.parse("""
                let value_1 = 1|2;
                """);
        ast.asf().infer();
        assertFalse(ast.errors().isEmpty());
        assertEquals(GCPErrCode.INVALID_OPTION_EXPRESSION, ast.errors().getFirst().errorCode());
    }

    // ── Built-in global function inference ────────────────────────────────────

    @Test
    void test_builtin_print_infers_unit() {
        AST ast = RunnerHelper.parse("print(42)");
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
    }

    @Test
    void test_builtin_to_str_infers_string() {
        AST ast = RunnerHelper.parse("let s = to_str(123);");
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ((VariableDeclarator)
                ast.program().body().statements().getFirst()).assignments().getFirst().lhs().outline();
        assertInstanceOf(STRING.class, outline);
    }

    @Test
    void test_builtin_to_int_infers_int() {
        AST ast = RunnerHelper.parse("let n = to_int(3.14);");
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ((VariableDeclarator)
                ast.program().body().statements().getFirst()).assignments().getFirst().lhs().outline();
        assertInstanceOf(INTEGER.class, outline);
    }

    @Test
    void test_builtin_to_float_infers_float() {
        AST ast = RunnerHelper.parse("let f = to_float(10);");
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ((VariableDeclarator)
                ast.program().body().statements().getFirst()).assignments().getFirst().lhs().outline();
        assertInstanceOf(FLOAT.class, outline);
    }

    @Test
    void test_builtin_to_number_infers_number() {
        AST ast = RunnerHelper.parse("let n = to_number(\"42\");");
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ((VariableDeclarator)
                ast.program().body().statements().getFirst()).assignments().getFirst().lhs().outline();
        assertInstanceOf(NUMBER.class, outline);
    }

    @Test
    void test_builtin_len_infers_int() {
        AST ast = RunnerHelper.parse("let n = len(\"hello\");");
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ((VariableDeclarator)
                ast.program().body().statements().getFirst()).assignments().getFirst().lhs().outline();
        assertInstanceOf(INTEGER.class, outline);
    }

    @Test
    void test_builtin_assert_infers_unit() {
        AST ast = RunnerHelper.parse("assert(true)");
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
    }

    @Test
    void test_builtin_print_accepts_string() {
        AST ast = RunnerHelper.parse("print(\"hello\")");
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
    }

    @Test
    void test_builtin_to_str_accepts_bool() {
        AST ast = RunnerHelper.parse("let s = to_str(true);");
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ((VariableDeclarator)
                ast.program().body().statements().getFirst()).assignments().getFirst().lhs().outline();
        assertInstanceOf(STRING.class, outline);
    }

    @Test
    @Disabled("known-incomplete: ~this propagation through chained filter() loses entity context")
    void test_chained_filter_this_preserved() {
        AST ast = ASTHelper.mockChainedFilterThis();
        ast.asf().infer();
        assertTrue(ast.inferred());
        assertTrue(ast.errors().isEmpty(), "Expected no errors but got: " + ast.errors());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("(Integer,Integer)", outline.toString());
    }

    @Test
    void test_lambda_param_merged_from_assignment_and_member_access() {
        // Regression: x should be inferred as {name:String, age:Integer}, not just {name:String}.
        // `y = x` contributes x.hasToBe = {name:String};
        // `x.age - 1` contributes x.definedToBe = {age:Number}.
        // MetaExtractor.resolveOutline() must use min() to merge both constraints.
        AST ast = RunnerHelper.parse("""
                let f = x->{
                    var y = {name="will"};
                    y = x;
                    let age = x.age-1;
                    return age;
                };
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "Expected no errors but got: " + ast.errors());

        FirstOrderFunction f = cast(lhsOf(ast, 0));
        Genericable<?, ?> xParam = cast(f.argument());
        Outline resolved = org.twelve.gcp.meta.MetaExtractor.resolveOutline(xParam);

        assertInstanceOf(ProductADT.class, resolved,
                "x should resolve to a record/entity type, got: " + resolved);
        ProductADT entity = cast(resolved);

        assertTrue(entity.getMember("name").isPresent(),
                "x must have field 'name' (inferred from y=x where y:{name:String})");
        assertTrue(entity.getMember("age").isPresent(),
                "x must have field 'age' (inferred from x.age-1)");
    }

    /**
     * 覆盖 Genericable.guess() / toString() 路径（playground API 的 walkForSymbols 使用此路径）。
     * min() 合并后的结果必须同时含有 name 和 age 两个字段，
     * 函数类型字符串也必须包含 age（即 playground 展示时不能只显示 {name:String}）。
     */
    @Test
    void test_lambda_param_guess_and_toString_include_both_fields() {
        AST ast = RunnerHelper.parse("""
                let f = x->{
                    var y = {name="will"};
                    y = x;
                    let age = x.age-1;
                    return age;
                };
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "Expected no errors but got: " + ast.errors());

        FirstOrderFunction f = cast(lhsOf(ast, 0));
        Genericable<?, ?> xParam = cast(f.argument());

        // min() must contain both fields (the merged lower bound)
        Outline min = xParam.min();
        assertInstanceOf(ProductADT.class, min,
                "xParam.min() should be a ProductADT, got: " + min + " [" + min.getClass().getSimpleName() + "]");
        ProductADT minEntity = cast(min);
        assertTrue(minEntity.getMember("name").isPresent(),
                "xParam.min() must have 'name', hasToBe=" + xParam.hasToBe() + ", definedToBe=" + xParam.definedToBe());
        assertTrue(minEntity.getMember("age").isPresent(),
                "xParam.min() must have 'age', hasToBe=" + xParam.hasToBe() + ", definedToBe=" + xParam.definedToBe());

        // guess() must also contain both fields (used by Genericable.toString())
        Outline guessed = xParam.guess();
        assertInstanceOf(ProductADT.class, guessed,
                "xParam.guess() should be a ProductADT, got: " + guessed);
        ProductADT guessEntity = cast(guessed);
        assertTrue(guessEntity.getMember("name").isPresent(),
                "xParam.guess() must have 'name'");
        assertTrue(guessEntity.getMember("age").isPresent(),
                "xParam.guess() must have 'age'");

        // The function's toString() (used by walkForSymbols in the playground backend)
        // must show the parameter as a record containing age.
        // e.g. "{name:String, age:Number} -> Number" or similar — must NOT be just "{name:String}"
        Outline fOutline = lhsOf(ast, 0);
        String funcType = fOutline.toString();
        assertTrue(funcType.contains("age"),
                "Function toString() must mention 'age', but got: " + funcType
                + " (xParam.hasToBe=" + xParam.hasToBe() + ", xParam.definedToBe=" + xParam.definedToBe() + ")");
    }

    @Test
    void test_curried_lift_hof() {
        AST ast = RunnerHelper.parse("""
                let lift = sel -> pred -> entity -> pred(sel(entity));
                let get_score  = player -> player.score;
                let is_passing = s -> s >= 60;
                let is_ace     = s -> s >= 90;
                let check_pass = lift(get_score)(is_passing);
                let check_ace  = lift(get_score)(is_ace);
                let r1 = check_pass({ name = "Alice", score = 85 });
                let r2 = check_ace ({ name = "Bob",   score = 95 });
                let r3 = check_pass({ name = "Carol", score = 55 });
                r2;
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "Expected no errors but got: " + ast.errors());
        Outline r2 = ast.program().body().statements().getLast().nodes().getFirst().outline();
        assertEquals(ast.Boolean.toString(), r2.toString(), "r2 should be Bool");
    }

    @Test
    void test_curried_lift_minimal() {
        // Minimal: 2-level curried, no comparison
        AST ast = RunnerHelper.parse("""
                let f = sel -> pred -> x -> pred(sel(x));
                let getV = a -> a.v;
                let ident = b -> b;
                let t = f(getV)(ident);
                t({ v = 42 });
                """);
        ast.asf().infer();
        System.out.println("Minimal errors: " + ast.errors());

        // Is it the global to_str that leaks? Try without any stdlibs potentially interfering
        // Test: 2-level but not 3-level
        AST ast2 = RunnerHelper.parse("""
                let apply = sel -> x -> sel(x);
                let getV = a -> a.v;
                let t = apply(getV);
                t({ v = 42 });
                """);
        ast2.asf().infer();
        System.out.println("2-level errors: " + ast2.errors());
    }

    @Test
    @Disabled("known-incomplete: this-return chain produces spurious 'value cannot be assigned to this binding' error")
    void test_this_return_chain_no_stackoverflow() {
        // Chaining methods that return `this{...}` must not cause StackOverflowError.
        // The Lazy.eventual() cycle guard prevents infinite mutual recursion between
        // MemberAccessorInference and Lazy.eventual() during multi-method chains.
        AST ast = RunnerHelper.parse("""
                outline Base = <i, o> {
                    data: [i],
                    map:  (f: i -> o) -> this{data = data.map(d -> f(d))}
                };
                outline Stream = <i> Base<i> {
                    filter: (pred: i -> Bool) -> this{data = data.filter(d -> pred(d))}
                };
                let s = Stream { data = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] };
                let result = s
                    .filter(x -> x % 2 == 0)
                    .map(x -> x * x);
                result.data;
                """);
        assertTrue(ast.asf().infer(), "infer() should return true without StackOverflow");
        assertTrue(ast.errors().isEmpty(), "Expected no errors but got: " + ast.errors());
        Outline resultData = ast.program().body().statements().getLast().nodes().getFirst().outline();
        assertTrue(resultData.toString().contains("["), "result.data should be an Array, got: " + resultData);
    }
}
