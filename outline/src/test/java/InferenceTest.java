import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.exception.GCPErrCode;
import org.twelve.gcp.node.expression.*;
import org.twelve.gcp.node.imexport.Export;
import org.twelve.gcp.node.imexport.Import;
import org.twelve.gcp.node.statement.Assignment;
import org.twelve.gcp.node.statement.Statement;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.gcp.outline.Outline;
import org.twelve.gcp.outline.adt.*;
import org.twelve.gcp.outline.builtin.ERROR;
import org.twelve.gcp.outline.primitive.DOUBLE;
import org.twelve.gcp.outline.primitive.INTEGER;
import org.twelve.gcp.outline.primitive.STRING;
import org.twelve.gcp.outline.projectable.FirstOrderFunction;
import org.twelve.gcp.outline.projectable.Function;
import org.twelve.gcp.outline.projectable.Genericable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.twelve.gcp.common.Tool.cast;

public class InferenceTest {


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
        List<EntityMember> ms = person.members().stream().filter(m->!m.isDefault()).toList();
        assertEquals(3, ms.size());
        EntityMember name = ms.get(0);
        EntityMember getName = ms.get(1);
        EntityMember getName2 = ms.get(2);
        assertInstanceOf(STRING.class, name.outline());
        assertInstanceOf(Function.class, getName.outline());
        assertInstanceOf(STRING.class, ((Function<?, ?>) getName.outline()).returns().supposedToBe());
        assertInstanceOf(STRING.class, ((Function<?, ?>) getName2.outline()).returns().supposedToBe());

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
        List<EntityMember> ms = person.members().stream().filter(m->!m.isDefault()).toList();
        assertEquals(2, ms.size());
        EntityMember name = ms.get(0);
        EntityMember getName = ms.get(1);
        assertInstanceOf(STRING.class, name.outline());
        assertInstanceOf(Function.class, getName.outline());
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
        List<EntityMember> ms =  person.members().stream().filter(m->!m.isDefault()).toList();
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
        List<EntityMember> ms =  person.members().stream().filter(m->!m.isDefault()).toList();
        assertEquals(4, ms.size());
        EntityMember getFullName = ms.getFirst();
        assertInstanceOf(Function.class, getFullName.outline());
        assertInstanceOf(STRING.class, ((Function<?, ?>) getFullName.outline()).returns().supposedToBe());
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
        Entity person = cast(ast.program().body().statements().get(3).nodes().getFirst().nodes().getFirst().outline());
        List<EntityMember> members = person.members().stream().filter(m->!m.isDefault()).toList();
        assertEquals(4, members.size());
        assertInstanceOf(STRING.class, members.get(1).outline());
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
}
