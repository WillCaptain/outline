import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.ast.Token;
import org.twelve.gcp.common.VariableKind;
import org.twelve.gcp.exception.GCPErrCode;
import org.twelve.gcp.node.expression.Identifier;
import org.twelve.gcp.node.expression.LiteralNode;
import org.twelve.gcp.node.expression.accessor.MemberAccessor;
import org.twelve.gcp.node.expression.body.FunctionBody;
import org.twelve.gcp.node.expression.referable.ReferenceCallNode;
import org.twelve.gcp.node.expression.typeable.IdentifierTypeNode;
import org.twelve.gcp.node.function.Argument;
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

public class GCPInference {
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
         * let f = fx<a>(x:a->{name:a,age:Integer})->{
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
        let factorial = n -> n==0?1:n*factorial(n-1);
        factorial(100);
        factorial("100");
         */
        AST ast = ASTHelper.mockRecursive();
        ast.asf().infer();
        VariableDeclarator declarator = cast(ast.program().body().statements().getFirst());
        Function<?,?> f = cast(declarator.assignments().getFirst().lhs().outline());
        Genericable<?,?> argument = cast(f.argument());
        Returnable returns = f.returns();
        assertInstanceOf(NUMBER.class, argument.definedToBe());
        assertInstanceOf(INTEGER.class, returns.supposedToBe());
        assertEquals(1,ast.errors().size());
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
        let f = fx<a,b>(x:a)->{
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
        Node g = ast.program().body().statements().get(1).nodes().getFirst().nodes().getFirst();
        Node h = ast.program().body().statements().get(2).nodes().getFirst().nodes().getFirst();
        Node will = ast.program().body().statements().get(3).nodes().getFirst().nodes().getFirst();
        Node age = ast.program().body().statements().get(4).nodes().getFirst().nodes().getFirst();

        assertEquals("{0: {0: Integer,1: String},1: <b>}",g.outline().toString());
        assertEquals("{0: {0: String,1: String},1: Integer}",h.outline().toString());
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
}
