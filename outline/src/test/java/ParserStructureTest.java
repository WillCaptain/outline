import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.node.imexport.Export;
import org.twelve.gcp.node.imexport.ExportSpecifier;
import org.twelve.gcp.node.imexport.Import;
import org.twelve.gcp.node.imexport.ImportSpecifier;
import org.twelve.gcp.node.statement.Statement;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.gcp.outline.builtin.Module;
import org.twelve.gcp.outline.builtin.Namespace;
import org.twelve.gcp.outline.builtin.UNKNOWN;
import org.twelve.outline.OutlineParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.twelve.gcp.common.Tool.cast;

public class ParserStructureTest {
    private ASF asf;
    private AST ast;

    @BeforeEach
    @SneakyThrows
    void before() {
        String code = """
                module org.twelve.human
                import grade as level, college as school from education;
                import * from e.f.g;
                let age: Int = 10, name = "Will", height: Double = 1.68, grade = level;
                export height as stature, name;""";
        this.asf = new ASF();
        this.ast = new OutlineParser().parse(code);
    }

    @Test
    void test_namespace() {
        assertEquals(7, this.ast.program().namespace().loc().start());
        assertEquals(16, this.ast.program().namespace().loc().end());

        assertEquals("org", ast.program().namespace().nodes().get(0).lexeme());
        assertEquals("twelve", ast.program().namespace().nodes().get(1).lexeme());
        assertEquals("human", ast.program().moduleName());
        assertEquals("org.twelve", ast.program().namespace().lexeme());

        //like outline of "package org.twelve.test" is unknown before inference
        assertInstanceOf(UNKNOWN.class, ast.program().namespace().outline());
        Namespace org = cast(ast.program().namespace().nodes().getFirst().outline());
        // but outline of "org" is namespace
        assertInstanceOf(Namespace.class, org);
        assertTrue(org.isTop());
        assertNull(org.parentNamespace());
        assertEquals("org", org.namespace());
        //namespace is not assignable, means or can not express alone without tail
        assertFalse(org.beAssignedAble());

        Namespace twelve = cast(ast.program().namespace().nodes().get(1).outline());
        assertEquals(twelve, org.subNamespaces().getFirst());
        assertEquals(org, twelve.parentNamespace());
        assertEquals("twelve", twelve.namespace());

        assertEquals("org.twelve", ast.namespace().lexeme());

        assertEquals(0, ast.program().id());
        assertEquals(2, ast.program().body().id());
        assertEquals(6, ast.program().namespace().id());
        assertEquals(8, ast.program().namespace().nodes().get(0).id());
        assertEquals(10, ast.program().namespace().nodes().get(1).id());
    }
    @Test
    void test_import() {
        Import imported = this.ast.program().body().imports().getFirst();
        //check to string
        assertEquals("import grade as level, college as school from education;", ast.program().body().imports().getFirst().lexeme());
        //check location
        assertEquals(23, imported.loc().start());
        assertEquals(79, imported.loc().end());
        //check source
        assertEquals("education", imported.source().lexeme());
        assertEquals(69, imported.source().loc().start());
        assertEquals(77, imported.source().loc().end());
        assertInstanceOf(Module.class, imported.source().outline());
        //check a
        ImportSpecifier a = imported.specifiers().getFirst();
        assertEquals("grade as level", a.lexeme());
        assertEquals("grade", a.imported().lexeme());
        assertEquals(30, a.imported().loc().start());
        assertEquals(34, a.imported().loc().end());
        assertEquals("level", a.local().lexeme());
        assertEquals(39, a.local().loc().start());
        assertEquals(43, a.local().loc().end());

        assertInstanceOf(UNKNOWN.class, a.get(0).outline());//outline is not confirmed yet
        assertSame(a.get(0).outline(), a.get(1).outline());//outline of b is a reference of a outline
        //check c
        ImportSpecifier c = imported.specifiers().get(1);
        assertEquals("school", c.get(1).toString());
        assertEquals("college as school", c.lexeme());
        assertEquals("college", c.imported().lexeme());
        assertEquals(46, c.imported().loc().start());
        assertEquals(52, c.imported().loc().end());

        //import * from e
        imported = this.ast.program().body().imports().getLast();
        assertEquals("e.f.g", imported.source().lexeme());
        a = imported.specifiers().getFirst();
        assertEquals("*", a.lexeme());
        //check location
        assertEquals(80, imported.loc().start());
        assertEquals(100, imported.loc().end());

    }
    @Test
    void test_export() {
        Export exported = ast.program().body().exports().getFirst();
        //check to string
        assertEquals("export height as stature, name;", ast.program().body().exports().getFirst().toString());
        //check location
        assertEquals(173, exported.loc().start());
        assertEquals(204, exported.loc().end());
        //check a
        ExportSpecifier a = exported.specifiers().getFirst();
        assertEquals("height as stature", a.lexeme());
        assertEquals("height", a.local().lexeme());
        assertEquals(180, a.local().loc().start());
        assertEquals(185, a.local().loc().end());
        assertEquals("stature", a.exported().lexeme());
        assertEquals(190, a.exported().loc().start());
        assertEquals(196, a.exported().loc().end());

        assertInstanceOf(UNKNOWN.class, a.get(0).outline());//outline is not confirmed yet
        assertSame(a.get(0).outline(), a.get(1).outline());//outline of b is a reference of a outline
        //check c
        ExportSpecifier c = exported.specifiers().get(1);
        assertSame(c.get(0), c.get(1));
        assertEquals("name", c.lexeme());
        assertEquals("name", c.local().lexeme());
        assertEquals(199, c.local().loc().start());
        assertEquals(202, c.local().loc().end());
    }
    @Test
    void test_variable_declare() {
        List<Statement> stmts = this.ast.program().body().statements();
        VariableDeclarator var = cast(stmts.getFirst());
        assertEquals(105, var.loc().start());
        assertEquals(170, var.loc().end());
        assertEquals("let age: Integer = 10, name = \"Will\", height: Double = 1.68, grade = level;",
                var.toString());
    }

    @Test
    void test_function_definition(){
        AST ast = ASTHelper.mockAddFunc();
        String expected = """
                module default
                
                let add = x->y->x+y;
                let add_2 = x->y->{
                  let z = x+y;
                  z
                };""";
        assertEquals(expected,ast.lexeme());
    }
}
