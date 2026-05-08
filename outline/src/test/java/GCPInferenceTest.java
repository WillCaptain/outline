import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.Token;
import org.twelve.gcp.common.VariableKind;
import org.twelve.gcp.exception.GCPErrCode;
import org.twelve.gcp.exception.GCPError;
import org.twelve.gcp.interpreter.value.StringValue;
import org.twelve.gcp.interpreter.value.Value;
import org.twelve.gcp.meta.FieldMeta;
import org.twelve.gcp.meta.MetaExtractor;
import org.twelve.gcp.node.expression.Assignment;
import org.twelve.gcp.node.expression.accessor.MemberAccessor;
import org.twelve.gcp.node.expression.identifier.Identifier;
import org.twelve.gcp.node.expression.LiteralNode;
import org.twelve.gcp.node.expression.body.FunctionBody;
import org.twelve.gcp.node.expression.referable.ReferenceCallNode;
import org.twelve.gcp.node.expression.typeable.IdentifierTypeNode;
import org.twelve.gcp.node.function.FunctionCallNode;
import org.twelve.gcp.node.function.FunctionNode;
import org.twelve.gcp.node.statement.*;
import org.twelve.gcp.outline.Outline;
import org.twelve.gcp.outline.adt.ADT;
import org.twelve.gcp.outline.adt.Entity;
import org.twelve.gcp.outline.adt.EntityMember;
import org.twelve.gcp.outline.primitive.INTEGER;
import org.twelve.gcp.outline.primitive.LONG;
import org.twelve.gcp.outline.primitive.NUMBER;
import org.twelve.gcp.outline.primitive.STRING;
import org.twelve.gcp.outline.projectable.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.twelve.gcp.common.Tool.cast;

public class GCPInferenceTest {

    @BeforeAll
    static void warmUp() {
        ASTHelper.parser.toString();
    }

    @Test
    void declared_entity_parameter_rejects_missing_accessed_field() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                let create = (x:{age:Int})->{
                    x.name
                };
                """);
        ast.asf().infer();
        assertTrue(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.FIELD_NOT_FOUND),
                "declared x:{age:Int} should reject x.name; got: " + ast.errors());
    }

    @Test
    void match_pattern_with_wildcard_does_not_restrict_argument() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline Human = Male{name:String, age:Int}, Pet = Dog;
                let get_name = someone -> match someone {
                    Male{name} -> name,
                    _ -> {other = 100f}
                };
                let pet = Dog;
                get_name(pet)
                """);
        ast.asf().infer();
        assertFalse(ast.errors().stream().anyMatch(e -> e.severity() == GCPError.Severity.ERROR),
                "match pattern should not become a hard argument constraint; got: " + ast.errors());
        assertEquals("{other: Float}", ast.program().body().statements().getLast().outline().toString());
    }

    @Test
    void match_could_be_mismatch_is_warning_only() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline Human = Male{name:String}, Pet = Dog | Cat;
                let check = (pet:Pet) -> match pet {
                    Male{name} -> 1,
                    _ -> 0
                };
                """);
        ast.asf().infer();
        assertTrue(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.NON_EXHAUSTIVE_MATCH
                        && e.severity() == GCPError.Severity.WARNING),
                "could_be mismatch should be reported as a warning; got: " + ast.errors());
        assertFalse(ast.errors().stream().anyMatch(e -> e.severity() == GCPError.Severity.ERROR),
                "could_be mismatch must not become a hard error; got: " + ast.errors());
    }

    @Test
    void enum_field_to_str_infers_interprets_and_completes() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline Status = Approved|Rejected|Pending|Fail;

                let create = (x:{status:Status})->{
                    x.status.to_str()
                };

                let status = create({status:Status.Pending});
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "" + ast.errors());

        Value result = ast.asf().interpret();
        assertInstanceOf(StringValue.class, result);
        assertEquals("Pending", ((StringValue) result).value());

        MemberAccessor statusAccess = findMemberAccessor(ast.program(), "x.status");
        assertNotNull(statusAccess);
        List<FieldMeta> fields = MetaExtractor.fieldsOf(statusAccess.outline(), ast.sourceCode());
        assertTrue(fields.stream().anyMatch(field -> "to_str".equals(field.name())),
                "Status field completion should include to_str; got: " + fields);
    }

    @Test
    @org.junit.jupiter.api.Disabled("TODO: re-enable after surgical fix for `Concrete.is(Lazy)` / `Concrete.is(Option(Lazy))`; "
            + "depends on the same fix as lazy_named_entity_return_satisfies_declared_relation_function_type. "
            + "Previous attempt (gcp commit 08596c5) caused infinite recursion in self-referential generic projection.")
    void nullable_named_entity_relation_return_preserves_target_type() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline ItemStatus = PENDING|APPROVED|EXPIRED;
                outline Computer = {
                    id:Int,
                    serial_num:String?,
                    status:ItemStatus?,
                    update:{serial_num:String?, status:ItemStatus?} -> Unit
                };
                outline Employee = {
                    id:Int,
                    computer_id:Int?,
                    computer:Unit -> Computer?
                };

                let activate = (employee: Employee, serial_number: String) -> {
                    let computer = employee.computer();
                    if (computer is Computer) {
                        computer.update({serial_num = serial_number, status = ItemStatus.APPROVED});
                    }
                };
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "expected nullable relation to narrow to Computer, got: " + ast.errors());
    }

    @Test
    void outline_decl_after_throwing_rhs_still_registered() {
        // Regression: prior to the resilient OutlineDefinitionInference fix,
        // a fatal Throwable (e.g. StackOverflowError from recursive generic
        // projection on `VirtualSet<X>{...}`) on one outline rhs caused every
        // subsequent `outline X = …` declaration in the same body scope to be
        // silently dropped. Downstream `is X` narrowing then resolved X to a
        // bare SYMBOL placeholder with no members. We simulate the failure by
        // assigning a known-bad rhs and verifying the next outline declaration
        // still registers and exposes its members.
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline Bad = doesNotExist<NotAType>{also: bogus};
                outline Computer = { id:Int, serial_num:String? };
                let computer:Computer? = null;
                if (computer is Computer) {
                    computer.id
                }
                """);
        ast.asf().infer();
        assertTrue(ast.errors().stream().noneMatch(e -> e.errorCode() == GCPErrCode.FIELD_NOT_FOUND),
                "expected `outline Computer` after a failing earlier rhs to still register; got: " + ast.errors());
    }

    @Test
    void direct_typed_local_member_access() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline Computer = { id:Int, serial_num:String? };
                let computer:Computer? = null;
                computer.id
                """);
        ast.asf().infer();
        // Computer? is nullable — direct .id should fail (Nothing has no id),
        // but the diagnostic tells us if Computer itself resolves correctly:
        System.err.println("direct typed errs: " + ast.errors());
    }

    @Test
    void nullable_local_is_narrowing_member_access() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline ItemStatus = PENDING|APPROVED|EXPIRED;
                outline Computer = {
                    id:Int,
                    serial_num:String?,
                    status:ItemStatus?,
                    update:{serial_num: String?, status: ItemStatus?} -> Unit,
                    delete:Unit -> Unit
                };
                let computer:Computer? = null;
                if (computer is Computer) {
                    computer.id
                }
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "expected `computer is Computer` to narrow Computer? -> Computer for member access; got: " + ast.errors());
    }

    @Test
    void untyped_lambda_relation_narrowing_in_if_body() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline ItemStatus = PENDING|APPROVED|EXPIRED;
                outline Computer = {
                    id:Int,
                    serial_num:String?,
                    status:ItemStatus?,
                    update:{serial_num:String?, status:ItemStatus?} -> Unit
                };
                outline Employee = {
                    id:Int,
                    computer_id:Int?,
                    computer:Unit -> Computer?
                };

                let activate = (employee) -> {
                    let computer = employee.computer();
                    if (computer is Computer) {
                        computer.update({serial_num = "SN", status = ItemStatus.APPROVED});
                    }
                };
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "expected untyped lambda to still narrow `computer is Computer`, got: " + ast.errors());
    }

    @Test
    void nullable_relation_disjoint_is_reports_unreachable_cast() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline ItemStatus = PENDING|APPROVED|EXPIRED;
                outline Computer = {
                    id:Int,
                    serial_num:String?,
                    status:ItemStatus?,
                    update:{serial_num:String?, status:ItemStatus?} -> Unit
                };
                outline Employee = {
                    id:Int,
                    computer_id:Int?,
                    computer:Unit -> Computer?
                };

                let f = (employee: Employee) -> {
                    let computer = employee.computer();
                    if (computer is Employee) {
                    }
                };
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().stream().anyMatch(e -> e.errorCode() == GCPErrCode.TYPE_CAST_NEVER_SUCCEED),
                "expected impossible `computer is Employee` to surface TYPE_CAST_NEVER_SUCCEED; got: " + ast.errors());
    }

    @Test
    @org.junit.jupiter.api.Disabled("TODO: re-enable after surgical fix for `Concrete.is(Option(Lazy))`; "
            + "the previous attempt (commit 08596c5 in gcp) made Lazy.is/tryIamYou call eventual(), "
            + "which caused infinite recursion in self-referential generic projection (VirtualSet<T>{...}). "
            + "Reverted; needs an Option-aware compatibility check confined to the passive direction.")
    void lazy_named_entity_return_satisfies_declared_relation_function_type() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline ItemStatus = PENDING|APPROVED|EXPIRED;
                outline Computer = {
                    id:Int,
                    serial_num:String?,
                    status:ItemStatus?,
                    update:{serial_num:String?, status:ItemStatus?} -> Unit
                };
                outline Employee = {
                    id:Int,
                    computer_id:Int?,
                    computer:Unit -> Computer?
                };

                let employee:Employee = {
                    _computer:Computer? = null,
                    id = 100,
                    computer = () -> _computer
                };
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "expected lazy Computer? return to satisfy Unit -> Computer?, got: " + ast.errors());
    }

    @Test
    void generated_employee_schema_allows_typed_action_param_relation_access() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline EmployeeStatus = INITIALIZED|APPROVED|REJECTED|ACTIVE;
                outline ItemStatus = PENDING|APPROVED|EXPIRED;
                outline Department = { id:Int };
                outline SalaryRecord = { id:Int };
                outline Badge = { id:Int };
                outline Computer = {
                    id:Int,
                    serial_num:String?,
                    status:ItemStatus?,
                    update:{serial_num: String?, status: ItemStatus?} -> Unit
                };
                outline Employee = {
                    id:Int,
                    name:String,
                    email:String,
                    gender:String,
                    sync_status:String,
                    status:EmployeeStatus,
                    department_id:Int?,
                    salary_id:Int?,
                    computer_id:Int?,
                    badge_id:Int?,
                    apply_pc_id:Int,
                    define_salary_id:Int,
                    issue_badge_id:Int,
                    department:Unit -> Department?,
                    salary:Unit -> SalaryRecord?,
                    computer:Unit -> Computer?,
                    badge:Unit -> Badge?,
                    apply_pc:Unit -> Computer,
                    define_salary:Unit -> SalaryRecord,
                    issue_badge:Unit -> Badge,
                    register_onboarding:Unit -> Unit,
                    register:Unit -> ?,
                    activate:String -> ?,
                    update:{name: String?, email: String?, gender: String?, sync_status: String?, status: EmployeeStatus?, department_id: Int?, salary_id: Int?, computer_id: Int?, badge_id: Int?, apply_pc_id: Int?, define_salary_id: Int?, issue_badge_id: Int?} -> Unit,
                    delete:Unit -> Unit
                };

                let activate = (employee: Employee) -> {
                    let computer = employee.computer();
                    computer
                };
                """);
        assertTrue(ast.asf().infer());
        assertTrue(ast.errors().isEmpty(), "expected generated Employee relation access to infer, got: " + ast.errors());
    }

    @Test
    void template_construction_rejects_wrong_nested_field_name() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline Decision = <a>{
                    payload:a,
                    name:String
                };
                outline Decision_1 = Decision<{gender:String}>;
                let decision_1 = Decision_1{payload:{gender2:"male"},name:"Evan"};
                """);
        ast.asf().infer();
        assertTrue(ast.errors().stream().anyMatch(e ->
                        e.errorCode() == GCPErrCode.OUTLINE_MISMATCH
                                || e.errorCode() == GCPErrCode.PROJECT_FAIL),
                "expected mismatch on payload field name; got: " + ast.errors());
    }

    @Test
    void template_construction_rejects_wrong_nested_field_name_simple() {
        AST ast = ASTHelper.parser.parse(new org.twelve.gcp.ast.ASF(), """
                outline D = {
                    payload:{gender:String}
                };
                let d = D{payload:{g:"male"}};
                """);
        ast.asf().infer();
        assertTrue(ast.errors().stream().anyMatch(e ->
                        e.errorCode() == GCPErrCode.OUTLINE_MISMATCH
                                || e.errorCode() == GCPErrCode.PROJECT_FAIL),
                "expected mismatch on payload field name; got: " + ast.errors());
    }

    @Test
    void test_gcp_declare_to_be() {
        /*
        let f = x:Integer->x;
        f("some");
        f(100);
         */
        AST ast = ASTHelper.mockGcpDeclareToBe();
        ast.asf().infer();
        FunctionNode f = cast(ast.program().body().get(0).get(0).get(1));
        LiteralNode<String> some = cast(ast.program().body().get(1).get(0).get(1));
        FunctionCallNode call2 = cast(ast.program().body().get(2).get(0));
        //f.outline is a function
        assertInstanceOf(FirstOrderFunction.class, f.outline());
        //f.argument is a Generic outline
        assertInstanceOf(Genericable.class, f.argument().outline());
        //f.argument.declared_to_be = Integer
        assertEquals(ast.Integer, f.argument().outline().declaredToBe());
        //f.return is a Return outline
        assertInstanceOf(Return.class, f.body().outline());
        //f.return = f.argument  (return x;)
        assertEquals(f.argument().outline(), ((Return) f.body().outline()).supposedToBe());
        //call1: gcp error
        assertEquals(1, ast.errors().size());
        assertEquals(some, ast.errors().getFirst().node());
        assertEquals(GCPErrCode.PROJECT_FAIL, ast.errors().getFirst().errorCode());
        //call2: Integer
        assertEquals(ast.Integer.toString(), call2.outline().toString());

    }

    private static MemberAccessor findMemberAccessor(Node node, String lexeme) {
        if (node instanceof MemberAccessor accessor && lexeme.equals(accessor.lexeme())) {
            return accessor;
        }
        for (Node child : node.nodes()) {
            MemberAccessor found = findMemberAccessor(child, lexeme);
            if (found != null) return found;
        }
        return null;
    }

    @Test
    void test_gcp_extend_to_be() {
        /*
         let f = x->{
           x = 10;
           x
         };
         f("some");
         f(100);
         */
        AST ast = ASTHelper.mockGcpExtendToBe();
        assertTrue(ast.asf().infer());

        Node f = ast.program().body().get(0).get(0).get(0);
        Return returns = cast(((FirstOrderFunction) f.outline()).returns());
        assertEquals(ast.Integer.toString(), ((Genericable<?,?>) returns.supposedToBe()).extendToBe().toString());

        //f("some") project fail
        Node call1 = ast.program().body().get(1).get(0);
        assertInstanceOf(INTEGER.class, call1.outline());
        assertEquals(GCPErrCode.PROJECT_FAIL, ast.errors().getFirst().errorCode());

        //f(10)
        Node call2 = ast.program().body().get(2).get(0);
        assertEquals(ast.Integer.toString(), call2.outline().toString());

    }

    @Test
    void test_gcp_has_to_be() {
        /*
        let f = x->{
            var y = "str";
            y = x;
            x
        };
        f("some");
        f(100);
         */
        AST ast = ASTHelper.mockGcpHasToBe();
        assertTrue(ast.asf().infer());
        Node f = ast.program().body().get(0).get(0).get(0);
        Node call1 = ast.program().body().get(2).get(0);
        Node call2 = ast.program().body().get(1).get(0);

        Return returns = cast(((FirstOrderFunction) f.outline()).returns());
        assertInstanceOf(STRING.class, ((Generic) returns.supposedToBe()).hasToBe());

        //f(100) project fail
        assertInstanceOf(STRING.class, call1.outline());
        assertEquals(GCPErrCode.PROJECT_FAIL, ast.errors().getFirst().errorCode());

        //f("some")
        assertInstanceOf(STRING.class, call2.outline());
    }

    @Test
    void test_gcp_defined_to_be() {
        /*
         let f = x->{
         x+1
         };
         f("some");
         f(100);
         */
        AST ast = ASTHelper.mockGcpDefinedToBe();
        assertTrue(ast.asf().infer());
        FunctionNode f = cast(ast.program().body().get(0).get(0).get(1));
        Node call1 = ast.program().body().get(1).get(0);
        Node call2 = ast.program().body().get(2).get(0);


        //f.outline is a function
        assertInstanceOf(FirstOrderFunction.class, f.outline());
        //f.argument is a Generic outline
        assertInstanceOf(Generic.class, f.argument().outline());
        //f.return is a Return outline
        assertInstanceOf(Addable.class, ((Return) f.body().outline()).supposedToBe());
        //f.return.suppose_to_be = f.argument  (return x;)
        assertTrue(f.argument().outline().definedToBe().is(ast.StringOrNumber));
        //call1: gcp error
        assertEquals(0, ast.errors().size());
        //call2: Integer
        assertInstanceOf(STRING.class, call1.outline());
        assertEquals(ast.Integer.toString(), call2.outline().toString());

    }

    @Test
    void test_gcp_add_expression() {
        //let f = (x,y)->x+y
        AST ast = ASTHelper.mockGcpAdd();
        assertTrue(ast.asf().infer());
        FunctionNode f = cast(ast.program().body().get(0).get(0).get(1));
        Node call1 = ast.program().body().get(1).get(0);
        Node call2 = ast.program().body().get(2).get(0);
        Node call3 = ast.program().body().get(4).get(0);
        //f.outline is a function
        assertInstanceOf(FirstOrderFunction.class, f.outline());
        //f.argument is a Generic outline
        assertInstanceOf(Generic.class, f.argument().outline());
        //f.return is a Return outline
        assertInstanceOf(Return.class, f.body().outline());
        //f.return.suppose_to_be = f.argument  (return x;)
        assertTrue(f.argument().outline().definedToBe().is(ast.StringOrNumber));
        //call1: gcp error
        assertEquals(0, ast.errors().size());
        //call2: float
        assertInstanceOf(STRING.class, call1.outline());
        assertEquals(ast.Float.toString(), call2.outline().toString());
        assertInstanceOf(STRING.class, call3.outline());
    }

    @Test
    void test_generic_refer_each_other() {
        /*
        let f = (x,y,z)->{
            y = x;
            z = y;
            x+y+z
        };
        f("some","people",10.0);
        f(10,10,10);
         */
        AST ast = ASTHelper.mockGenericReferEachOther();
        assertTrue(ast.asf().infer());
        Node call1 = ast.program().body().get(1).get(0);
        Node call2 = ast.program().body().get(2).get(0);
        Node floatNum = ast.program().body().get(1).get(0).get(3);
        assertEquals(1, ast.errors().size());
        assertEquals(floatNum, ast.errors().getFirst().node());
        assertEquals(ast.Integer.toString(), call2.outline().toString());
        assertInstanceOf(STRING.class, call1.outline());
    }

    @Test
    void test_gcp_hof_projection_1() {
        /*
        let f = (x,y)->y(x);
        f(10,x->x*5);
         */
        AST ast = ASTHelper.mockGcpHofProjection1();
        assertTrue(ast.asf().infer());
        Node call = ast.program().body().get(1).get(0);

        assertTrue(ast.errors().isEmpty());
        assertEquals(ast.Integer.toString(), call.outline().toString());
        assertTrue(ast.inferred());
    }

    @Test
    void test_gcp_hof_projection_2() {
        /*
        let f = (y,x)->y(x);
        f(x->x+5,"10");
         */
        AST ast = ASTHelper.mockGcpHofProjection2();
        assertTrue(ast.asf().infer());
        Node call = ast.program().body().get(1).get(0);
        assertTrue(ast.errors().isEmpty());
        assertInstanceOf(STRING.class, call.outline());
    }

    @Test
    void test_gcp_hof_projection_3() {
        /*
        let f = (x,y,z)->z(y(x));
        f(10,x->x+"some",y->y+100);
         */
        AST ast = ASTHelper.mockGcpHofProjection3();
        assertTrue(ast.asf().infer());
        Node call = ast.program().body().get(1).get(0);

        assertTrue(ast.errors().isEmpty());
        assertInstanceOf(STRING.class, call.outline());
    }

    @Test
    void test_gcp_hof_projection_4() {
        /*
        let f = (z,y,x)->z(y(x));
        f(y->y+100,x->x,10);
         */
        AST ast = ASTHelper.mockGcpHofProjection4();
        assertTrue(ast.asf().infer());
        Node call = ast.program().body().get(1).get(0);
        assertTrue(ast.errors().isEmpty());
        assertInstanceOf(INTEGER.class, call.outline());
    }

    @Test
    void test_gcp_declared_hof_projection(){
        /*
         * let f = fn<a>(x:a->{name:a,age:Integer})->{
         *   x("Will").name
         * };
         * f<Integer>;
         * f(n->{name=n,age=30})
         */
        AST ast = ASTHelper.mockDeclaredHofProjection();
        assertTrue(ast.asf().infer());
        assertEquals(1,ast.errors().size());
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().getFirst().errorCode());
        Outline name = ast.program().body().statements().get(2).outline();
        assertInstanceOf(STRING.class,name);
    }

    @Test
    void test_gcp_extend_hof_projection(){
        /*
         * let f = x->{
         *   x = a->{name=a};
         *   x("Will").name
         * };
         * f(n->{name=n})
         */
        AST ast = ASTHelper.mockExtendHofProjection();
        assertTrue(ast.asf().infer());
        Outline name = ast.program().body().statements().get(1).outline();
        assertInstanceOf(STRING.class,name);;
    }

    @Test
    void test_entity_hof_projection_1() {
        /*
        let f = (x,z,y)->z.combine(x,y);
        f(20,{combine = (x,y)->{{age = x,name = y.name}},{name = "Will"})
         */
        for (int i = 1; i < 4; i++) {
            AST ast = ASTHelper.mockEntityProjection1(i);
            assertTrue(ast.asf().infer());
            Node call = ast.program().body().get(1).get(0);
            Entity result = cast(call.outline());
            List<EntityMember> ms =  result.members().stream().filter(m->!m.isDefault()).toList();
            assertEquals("name", ms.get(0).name());
            assertInstanceOf(STRING.class, ms.get(0).outline());
            assertEquals("age", ms.get(1).name());
            assertInstanceOf(INTEGER.class, ms.get(1).outline());
            assertTrue(call.ast().errors().isEmpty());
            assertTrue(call.ast().inferred());
        }

    }

    @Test
    void test_entity_hof_projection_2() {
        /*
        let f = (x,z,y)->z.combine(x,y).name;
        f(20,{combine = (x,y)->{{age = x,name = y.name}},{name = "Will",})
         */
        for (int i = 1; i < 4; i++) {
            AST ast = ASTHelper.mockEntityProjection2(i);
            assertTrue(ast.asf().infer());
            Node call = ast.program().body().get(1).get(0);
            assertInstanceOf(STRING.class, call.outline());
        }
    }

    @Test
    void test_entity_hof_projection_3() {
        /*
        let f = (x,z,y)->z.combine(x,y).gender;
        f(20,{combine = (x,y)->{{age = x,name = y.name}},{name = "Will"})
         */
        for (int i = 1; i < 4; i++) {
            AST ast = ASTHelper.mockEntityProjection3(i);
            assertTrue(ast.asf().infer());
            Node call = ast.program().body().get(1).get(0);
            assertInstanceOf(AccessorGeneric.class, call.outline());
        }
    }

    @Test
    void test_entity_hof_projection_4() {
        /*
        let f = (x,z,y)->{
          var w = z;
          w.combine(x,y)
        };
        f(20,{combine = (x,y)->{{age = x,name = y.name}},{name = "Will"})
         */
        for (int i = 1; i < 4; i++) {
            AST ast = ASTHelper.mockEntityProjection4(i);
            assertTrue(ast.asf().infer());
            Node call = ast.program().body().get(1).get(0);
            Entity result = cast(call.outline());
            List<EntityMember> ms =  result.members().stream().filter(m->!m.isDefault()).toList();
            assertEquals("name",ms.get(0).name());
            assertInstanceOf(STRING.class, ms.get(0).outline());
            assertEquals("age", ms.get(1).name());
            assertInstanceOf(INTEGER.class, ms.get(1).outline());
        }

    }

    @Test
    void test_entity_hof_projection_5() {
        /*
        let f = (x,z,y)->{
          var w: {combine: Integer->{name: Integer}->{name: Integer}} = z;
          w.combine(x,y)
        };
        f(20,{combine = (x,y)->{{age = x,name = y.name}},{name = "Will"})
         */
        for (int i = 1; i < 4; i++) {
            AST ast = ASTHelper.mockEntityProjection5(i);
            assertTrue(ast.asf().infer());
            Node call = ast.program().body().get(1).get(0);
            Entity result = cast(call.outline());
            List<EntityMember> ms =  result.members().stream().filter(m->!m.isDefault()).toList();
            assertEquals("name", ms.getFirst().name());
            assertInstanceOf(INTEGER.class, ms.getFirst().outline());
            assertFalse(call.ast().errors().isEmpty());
        }

    }

    @Test
    void test_extend_entity_projection(){
        /*
        let f = <a>(person,gender:a)-> person{gender = gender};
        let g = f<String>;
        g("Will",1);
        f({name="Will"},1);
         */
        AST ast = ASTHelper.mockExtendEntityProjection();
        ast.asf().infer();
        assertTrue(ast.asf().inferred());
        FirstOrderFunction f = cast(ast.program().body().get(0).get(0).get(0).outline());
        FirstOrderFunction g = cast(ast.program().body().get(1).get(0).get(0).outline());
        assertInstanceOf(ADT.class, g.argument().definedToBe());
        FirstOrderFunction g1 = cast(g.returns().supposedToBe());
        assertInstanceOf(STRING.class, g1.argument().declaredToBe());
        ADT gRet = cast(g1.returns().supposedToBe());
        assertInstanceOf(STRING.class, gRet.getMember("gender").get().outline());
        ADT call1 = cast(ast.program().body().get(2).get(0).outline());
        assertInstanceOf(STRING.class, call1.getMember("gender").get().outline());
        assertInstanceOf(FirstOrderFunction.class, call1.getMember("to_str").get().outline());
        ADT call2 = cast(ast.program().body().get(3).get(0).outline());
        assertInstanceOf(INTEGER.class, call2.getMember("gender").get().outline());
        assertInstanceOf(STRING.class, call2.getMember("name").get().outline());
        assertEquals(1,ast.errors().size());
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().getFirst().errorCode());
    }
    @Test
    void test_gcp_recursive_projection(){
        /*
        let fib  = n -> if (n <= 1) n else fib(n - 1) + fib(n - 2);
        fib("100");
        fib(100)
         */
        AST ast = ASTHelper.mockSelfRecursive();
        ast.asf().infer();
        VariableDeclarator declarator = cast(ast.program().body().statements().getFirst());
        Function<?,?> f = cast(declarator.assignments().getFirst().lhs().outline());
        Genericable<?,?> argument = cast(f.argument());
        Returnable returns = f.returns();
        assertInstanceOf(NUMBER.class, argument.definedToBe());
        assertEquals("`Number`|(str|num)", returns.supposedToBe().toString());
        assertEquals(1,ast.errors().size());//这个错误来源于fib("100")，输入必须是number
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("Integer",outline.toString());
    }

    @Test
    void test_complicated_hof_projection(){
        AST ast = ASTHelper.mockComplicatedHofProjection();
        assertTrue(ast.asf().infer());
        Assignment f = cast(ast.program().body().statements().getFirst().nodes().getFirst());
        Outline l = f.lhs().outline();
        FunctionBody body =  cast(f.nodes().get(1).nodes().get(1).nodes().get(0).nodes().get(0).nodes().get(1).nodes().get(0).nodes().get(0).nodes().get(1));
        Outline x = body.nodes().get(0).nodes().get(0).get(0).outline();
        assertEquals("`<a>-><a>`",x.toString());
        Outline y = body.nodes().get(1).nodes().get(0).get(0).outline();
        assertEquals("`<a>-><a>`",y.toString());
        Outline z = body.nodes().get(2).get(0).nodes().get(1).outline();
        assertEquals("`<a>-><a>`",z.toString());
        Outline fstr = ast.program().body().statements().get(1).nodes().getFirst().outline();
        assertEquals("(String->String)->(String->String)->(String->String)->String->String",fstr.toString());
        Node refInt = ast.program().body().statements().get(2).nodes().getFirst();
        Outline fint = refInt.outline();
        assertEquals("(Integer->Integer)->(Integer->Integer)->(Integer->Integer)->Integer->Integer",fint.toString());
        assertEquals(1,ast.errors().size());
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().get(0).errorCode());
        assertEquals(refInt,ast.errors().get(0).node());
        Outline fcallx = ast.program().body().statements().get(3).nodes().get(0).outline();
        assertTrue(true);
    }

    @Test
    void test_extend_hasTo_defined_projection(){
        AST ast = ASTHelper.mockExtendHastoDefinedProjection();
        assertTrue(ast.asf().infer());
        Statement last = ast.program().body().statements().getLast();
        Outline outline = last.outline();
        assertEquals("String",outline.toString());
        assertEquals(1,ast.errors().size());
        assertEquals(last.get(0).get(1),ast.errors().getFirst().node());
    }

    @Test
    void test_multi_extend_projection(){
        /**
         * let f = x->y->{
         *   y = "Noble";
         *   y = x;
         *   y
         * };
         * let g = f("Will");
         * g("Zhang");
         * f(20,"Zhang")
         */
        AST ast = ASTHelper.mockMultiExtendProjection();
        assertTrue(ast.asf().infer());
        Outline g = ast.program().body().statements().get(1).nodes().get(0).nodes().get(0).outline();
        assertEquals("String->String",g.toString());
        assertEquals("String",ast.program().body().statements().get(2).nodes().get(0).outline().toString());
        assertEquals("String",ast.program().body().statements().get(3).nodes().get(0).outline().toString());
        assertEquals(1,ast.errors().size());
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().getFirst().errorCode());
    }

    @Test
    void test_multi_defined_projection(){
        /**
         * let f = x->{
         *   x.name;
         *   x.age;
         *   x
         * };
         * f({name:"Will",age:20,gender:"Male"});
         * f({name:"Will});
         */
        AST ast = ASTHelper.mockMultiDefinedProjection();
        assertTrue(ast.asf().infer());
        FirstOrderFunction f = cast(ast.program().body().statements().get(0).nodes().getFirst().nodes().getFirst().outline());
        assertEquals("{name: any,age: any}->{name: any,age: any}",f.toString());
        assertEquals("{gender: String,name: String,age: Integer}",ast.program().body().statements().get(1).nodes().get(0).outline().toString());
        assertEquals(1,ast.errors().size());
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().getFirst().errorCode());
        assertEquals("{\n  name = \"Will\"\n}",ast.errors().getFirst().node().toString());
    }

    @Test
    void test_gcp_only_reference_for_simple_function(){
        /*
        let f = fn<a,b>(x:a)->{
           let y:b = 100;
           y
        }
        let f1 = f<String,Long>;
        f<String,String>;//String(b) doesn't match Integer(y:b=100)
        f1(100);
        */
        AST ast = ASTHelper.mockReferenceInFunction();
        VariableDeclarator declarator = new VariableDeclarator(ast,VariableKind.LET);
        ReferenceCallNode rCall = new ReferenceCallNode(new Identifier(ast,new Token<>("f")),
                new IdentifierTypeNode(new Identifier(ast,new Token<>("String"))),
                new IdentifierTypeNode(new Identifier(ast,new Token<>("Long"))));
        declarator.declare(new Identifier(ast,new Token<>("f1")),rCall);
        ast.addStatement(declarator);
        rCall = new ReferenceCallNode(new Identifier(ast,new Token<>("f")),
                new IdentifierTypeNode(new Identifier(ast,new Token<>("String"))),
                new IdentifierTypeNode(new Identifier(ast,new Token<>("String"))));
        ast.addStatement(new ExpressionStatement(rCall));
        FunctionCallNode fCall = new FunctionCallNode(new Identifier(ast,new Token<>("f1")),LiteralNode.parse(ast,new Token<>(100)));
        ast.addStatement(new ReturnStatement(fCall));
        assertTrue(ast.asf().infer());
        Outline f1 = ast.program().body().statements().get(1).nodes().getFirst().nodes().getFirst().outline();
        assertInstanceOf(FirstOrderFunction.class,f1);
        assertInstanceOf(STRING.class,((FirstOrderFunction)f1).argument().declaredToBe());
        assertInstanceOf(LONG.class,((FirstOrderFunction)f1).returns().supposedToBe());
        Outline ret = ast.program().body().statements().getLast().outline();
        assertInstanceOf(LONG.class,ret);
        assertEquals(2,ast.errors().size());
        assertEquals(GCPErrCode.REFERENCE_MIS_MATCH,ast.errors().getFirst().errorCode());
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().getLast().errorCode());
    }

    @Test
    void test_gcp_argument_reference_for_simple_function(){
        /*
        let f = func<a,b>(x:a)->{
           let y:b = 100;
           y
        }
        f(100)
        */
        AST ast = ASTHelper.mockReferenceInFunction();
        FunctionCallNode fCall = new FunctionCallNode(new Identifier(ast,new Token<>("f")),LiteralNode.parse(ast,new Token<>(100)));
        ast.addStatement(new ReturnStatement(fCall));
        ast.asf().infer();
        Outline ret = ast.program().body().statements().getLast().outline();
        assertInstanceOf(INTEGER.class,ret);
    }

    @Test
    void test_gcp_only_reference_for_entity_return_function(){
        AST ast = ASTHelper.mockReferenceInEntity1();
        assertTrue(ast.asf().infer());
        //check initialed
        VariableDeclarator g = cast(ast.program().body().statements().getFirst());
        Function<?,?> goutline = cast(g.assignments().getFirst().lhs().outline());
        Entity originEntity = cast(goutline.returns().supposedToBe());
        EntityMember originZ = originEntity.members().getLast();
        assertEquals("<a>",originZ.outline().toString());
        assertEquals("<a>",originZ.node().outline().toString());

        //check g<Integer,String>
        Node rCall = ast.program().body().statements().get(1).get(0).get(1).get(0).get(0);
        Entity entity = cast(((Function<?,Genericable<?,?>>)rCall.outline()).returns().supposedToBe());
        EntityMember z = entity.members().getLast();
        assertInstanceOf(INTEGER.class,z.outline());
        assertInstanceOf(INTEGER.class,z.node().outline());
        //let f1 = g<Integer,String>().f;
        VariableDeclarator f1Declare = cast(ast.program().body().statements().get(1));
        FirstOrderFunction f1 = cast(f1Declare.assignments().getFirst().lhs().outline());
        Function<?,Genericable<?,?>> retF1 = cast(f1.returns().supposedToBe());
        assertInstanceOf(STRING.class, f1.argument().declaredToBe());
        assertInstanceOf(Reference.class, retF1.argument());
        assertEquals("c", retF1.argument().name());
        assertEquals("<c>", ((Genericable<?,?>)retF1.returns().supposedToBe()).toString());

        //let f2 = f1<Long>;
        VariableDeclarator f2Declare = cast(ast.program().body().statements().get(2));
        FirstOrderFunction f2 = cast(f2Declare.assignments().getFirst().lhs().outline());
        Function<?,Genericable<?,?>> retF2 = cast(f2.returns().supposedToBe());
        assertInstanceOf(LONG.class, retF2.argument().declaredToBe());
        assertInstanceOf(LONG.class, retF2.returns().supposedToBe());
    }

    @Test
    void test_array_projection(){
        AST ast = ASTHelper.mockArrayAsArgument();
        assertTrue(ast.asf().infer());
        //f([{name = "Will"}]) : {name: String};
        Entity outline_1 = cast(ast.program().body().statements().get(3).get(0).outline());
        List<EntityMember> ms =  outline_1.members().stream().filter(m->!m.isDefault()).toList();
        assertEquals("name",ms.get(0).name());
        assertInstanceOf(STRING.class,ms.get(0).outline());
        assertEquals(4,ast.errors().size());
        //f(100) : `any`;
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().get(1).errorCode());
        assertEquals("100",ast.errors().get(1).node().toString());
        //g(["a","b"],0) : String;
        Outline outline_2 = ast.program().body().statements().get(5).get(0).outline();
        assertInstanceOf(STRING.class,outline_2);
        //g([1],"idx") : Integer; plus "idx" mis match error
        Outline outline_3 = ast.program().body().statements().get(5).get(0).outline();
        assertInstanceOf(STRING.class,outline_3);
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().get(2).errorCode());
        assertEquals("[1]",ast.errors().get(2).node().toString());
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().get(3).errorCode());
        assertEquals("\"idx\"",ast.errors().get(3).node().toString());
        //r1([1,2]) : Integer;
        Outline outline_4 = ast.program().body().statements().get(8).get(0).outline();
        assertInstanceOf(INTEGER.class,outline_4);
        //let r2 = r<String>;
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().get(0).errorCode());
        assertEquals("r<String>",ast.errors().get(0).node().toString());
        //r([1,2]) : Integer;
        Outline outline_6 = ast.program().body().statements().get(10).get(0).outline();
        assertInstanceOf(INTEGER.class,outline_6);
    }
    @Test
    void test_dict_projection(){
        /*
         * let f = (x,y)->x[y];
         * let g = (x:[:],i)->{
         *     y = x[i];
         *     x = ["will":"zhang"];
         *     y
         * };
         * let r = <a>(x:[String:a])->{
         *     let b = ["Will":30];
         *     b = x;
         *     let c:a = x["Will"];
         *     c
         * }
         */
        AST ast = ASTHelper.mockDictAsArgument();
        assertTrue(ast.asf().infer());
        //f(["Will":"Zhang"],"Will") : String  x as map/dict
        Outline o1 = ast.program().body().statements().get(3).get(0).outline();
        assertEquals("String",o1.toString());
        //f(["Will"],0) : String   x as array
        Outline o2 = ast.program().body().statements().get(4).get(0).outline();
        assertEquals("String",o2.toString());
        //f(["Will":"Zhang"],0) : String 0 is wrong, should be String
        Node fcall3 = ast.program().body().statements().get(5).get(0);
        Outline o3 = fcall3.outline();
        assertEquals("String",o3.toString());
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().get(0).errorCode());
        assertEquals(fcall3,ast.errors().get(0).node().parent());
        //g(["Will":"Zhang"],0) : String
        Node fcall4 = ast.program().body().statements().get(6).get(0);
        Outline o4 = fcall4.outline();
        assertEquals("String",o4.toString());
        assertEquals(fcall4,ast.errors().get(1).node().parent());
        //let r1 = r<Integer>;
        Node fcall5 = ast.program().body().statements().get(7).get(0).get(0);
        Outline o5 = fcall5.outline();
        assertEquals("[String : Integer]->Integer",o5.toString());
        //r1(["Will":30]) : Integer
        Outline o6 = ast.program().body().statements().get(8).get(0).outline();
        assertEquals("Integer",o6.toString());
        //r2 = r<String>  constraint will cause project fail
        Node fcall7 = ast.program().body().statements().get(9).get(0);
        Outline o7 = fcall7.get(0).outline();
        assertEquals("[String : String]->String",o7.toString());
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().get(2).errorCode());
        assertEquals(fcall7,ast.errors().get(2).node().parent());
        Node fcall8 = ast.program().body().statements().get(10).get(0);
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().get(3).errorCode());
        assertEquals(fcall8,ast.errors().get(3).node().parent());
        assertEquals(4,ast.errors().size());

    }
    @Test
    void test_tuple_projection() {
        AST ast = ASTHelper.mockTupleProjection();
        assertTrue(ast.asf().infer());
        Node h = ast.program().body().statements().get(1).nodes().getFirst().nodes().getFirst();
        Node g = ast.program().body().statements().get(2).nodes().getFirst().nodes().getFirst();
        Node will = ast.program().body().statements().get(3).nodes().getFirst().nodes().getFirst();
        Node age = ast.program().body().statements().get(4).nodes().getFirst().nodes().getFirst();

        assertEquals("((Integer,String),<b>)",h.outline().toString());
        assertEquals("((String,String),Integer)",g.outline().toString());
        assertEquals("String",will.outline().toString());
        assertEquals("Integer",age.outline().toString());
        assertEquals(1,ast.errors().size());
        assertEquals(GCPErrCode.PROJECT_FAIL,ast.errors().getFirst().errorCode());

        ast = ASTHelper.mockGenericTupleProjection();
        assertTrue(ast.asf().infer());
        will = ast.program().body().statements().get(2).nodes().getFirst().nodes().getFirst();
        age = ast.program().body().statements().get(3).nodes().getFirst().nodes().getFirst();
        assertEquals("String",will.outline().toString());
        assertEquals("Integer",age.outline().toString());
    }

    @Test
    void test_self_return_gcp(){
        AST ast = ASTHelper.mockTypeSelfReturn();
        ast.asf().infer();
        assertTrue(ast.inferred());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("[String : Number]",outline.toString());
        assertFalse(ast.errors().isEmpty());
        assertEquals(GCPErrCode.OUTLINE_MISMATCH,ast.errors().getFirst().errorCode());
    }

    @Test
    void test_complex_gcp_back_propagation(){
        AST ast = ASTHelper.mockComplexPropagation();
        ast.asf().infer();
        // Print all variables' types and all errors for analysis
        System.err.println("--- inferred=" + ast.inferred() + " errors=" + ast.errors().size() + " ---");
        for (String name : new String[]{"lift","get_score","is_passing","is_ace","check_pass","check_ace","alice","pass","ace"}) {
            var sym = ast.symbolEnv().lookupAll(name);
            System.err.println("  SYM [" + name + "] : " + (sym != null ? sym.outline() : "NULL"));
        }
        ast.errors().forEach(e -> System.err.println("  ERR[" + e.errorCode() + "]: " + e.message().substring(0, Math.min(100, e.message().length()))));
        assertTrue(ast.inferred());
        assertFalse(ast.errors().isEmpty());//there must have inference error like:points not found, please fix
    }
}
