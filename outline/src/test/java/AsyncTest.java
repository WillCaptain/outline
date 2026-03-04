import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.interpreter.interpretation.BuiltinMethods;
import org.twelve.gcp.interpreter.value.*;
import org.twelve.gcp.outline.Outline;
import org.twelve.gcp.outline.adt.Promise;
import org.twelve.gcp.outline.projectable.FirstOrderFunction;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the {@code async}/{@code await} feature using the Outline source language.
 *
 * <p>All tests parse real GCP/Outline source strings, proving that the full pipeline
 * (lexer → parser → converter → inference → interpreter) supports {@code async} and {@code await}.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>Type inference: {@code async expr} → {@code Promise<T>}</li>
 *   <li>Type inference: {@code await promise} → {@code T}</li>
 *   <li>Type inference: callback members {@code .done} / {@code .error}</li>
 *   <li>Interpretation: {@code async} produces a {@link PromiseValue}</li>
 *   <li>Interpretation: {@code await} blocks and unwraps the value</li>
 *   <li>Interpretation: callback pattern via {@code .done} / {@code .error}</li>
 * </ul>
 */
public class AsyncTest {

    @BeforeAll
    static void warmUp() {
        ASTHelper.parser.toString();
    }

    // =========================================================================
    // Type inference
    // =========================================================================

    @Test
    void infer_async_integer_literal_is_promise_integer() {
        /*
         * let p = async 42;
         * p : Promise<Integer>
         */
        AST ast = ASTHelper.parser.parse("let p = async 42;");
        ast.asf().infer();

        assertTrue(ast.errors().isEmpty(), "Expected no errors, got: " + ast.errors());
        Outline outline = firstVarType(ast);
        assertInstanceOf(Promise.class, outline, "Expected Promise<Integer>, got: " + outline);
        assertInstanceOf(
                org.twelve.gcp.outline.primitive.INTEGER.class,
                ((Promise) outline).innerOutline(),
                "Inner type should be Integer");
    }

    @Test
    void infer_async_string_literal_is_promise_string() {
        /*
         * let p = async "hello";
         * p : Promise<String>
         */
        AST ast = ASTHelper.parser.parse("let p = async \"hello\";");
        ast.asf().infer();

        assertTrue(ast.errors().isEmpty(), "Expected no errors, got: " + ast.errors());
        Outline outline = firstVarType(ast);
        assertInstanceOf(Promise.class, outline);
        assertInstanceOf(
                org.twelve.gcp.outline.primitive.STRING.class,
                ((Promise) outline).innerOutline());
    }

    @Test
    void infer_async_lambda_is_promise_function() {
        /*
         * let p = async (x->x+1);
         * p : Promise<Integer -> Integer>
         */
        AST ast = ASTHelper.parser.parse("let p = async (x->x+1);");
        ast.asf().infer();

        assertTrue(ast.errors().isEmpty(), "Expected no errors, got: " + ast.errors());
        Outline outline = firstVarType(ast);
        assertInstanceOf(Promise.class, outline, "Expected Promise<fn>, got: " + outline);
        assertInstanceOf(FirstOrderFunction.class, ((Promise) outline).innerOutline());
    }

    @Test
    void infer_await_unwraps_promise_to_inner_type() {
        /*
         * let p = async 42;
         * let r = await p;
         * r : Integer
         */
        AST ast = ASTHelper.parser.parse("""
                let p = async 42;
                let r = await p;
                """);
        ast.asf().infer();

        assertTrue(ast.errors().isEmpty(), "Expected no errors, got: " + ast.errors());
        // p is statements[0], r is statements[1]
        Outline rType = varType(ast, 1);
        assertInstanceOf(
                org.twelve.gcp.outline.primitive.INTEGER.class,
                rType,
                "await p should have type Integer, got: " + rType);
    }

    @Test
    void infer_await_on_non_promise_reports_error() {
        /*
         * let x = 42;
         * let r = await x;   // type error: x is not a Promise
         */
        AST ast = ASTHelper.parser.parse("""
                let x = 42;
                let r = await x;
                """);
        ast.asf().infer();

        assertFalse(ast.errors().isEmpty(), "Expected at least one type error");
        assertTrue(
                ast.errors().stream().anyMatch(e ->
                        e.errorCode() == org.twelve.gcp.exception.GCPErrCode.OUTLINE_MISMATCH),
                "Expected OUTLINE_MISMATCH error, got: " + ast.errors());
    }

    @Test
    void infer_promise_then_member_is_function() {
        /*
         * let p = async 42;
         * let t = p.then;
         * t : (Integer -> b) -> Unit
         */
        AST ast = ASTHelper.parser.parse("""
                let p = async 42;
                let t = p.then;
                """);
        ast.asf().infer();

        assertTrue(ast.errors().isEmpty(), "Expected no errors, got: " + ast.errors());
        Outline tType = varType(ast, 1);
        assertInstanceOf(FirstOrderFunction.class, tType,
                "p.then should be a function type, got: " + tType);
    }

    @Test
    void infer_promise_catch_member_is_function() {
        /*
         * let p = async 42;
         * let c = p.catch;
         * c : (String -> b) -> Unit
         */
        AST ast = ASTHelper.parser.parse("""
                let p = async 42;
                let c = p.catch;
                """);
        ast.asf().infer();

        assertTrue(ast.errors().isEmpty(), "Expected no errors, got: " + ast.errors());
        Outline cType = varType(ast, 1);
        assertInstanceOf(FirstOrderFunction.class, cType,
                "p.catch should be a function type, got: " + cType);
    }

    @Test
    void infer_promise_toString_includes_type_name() {
        /*
         * Promise<Integer> toString should contain "Promise<"
         */
        AST ast = ASTHelper.parser.parse("let p = async 42;");
        ast.asf().infer();
        assertTrue(ast.errors().isEmpty());
        assertTrue(firstVarType(ast).toString().startsWith("Promise<"),
                "toString should start with 'Promise<', got: " + firstVarType(ast));
    }

    // =========================================================================
    // Interpretation – await
    // =========================================================================

    @Test
    void interpret_async_produces_promise_value() {
        /*
         * async 42    →   PromiseValue
         */
        Value v = RunnerHelper.run("async 42");
        assertInstanceOf(PromiseValue.class, v, "Expected PromiseValue, got: " + v);
    }

    @Test
    void interpret_await_async_integer() throws Exception {
        /*
         * let r = await (async 42);
         * r = 42
         */
        Value v = RunnerHelper.run("let r = await (async 42); r");
        assertInstanceOf(IntValue.class, v);
        assertEquals(42L, ((IntValue) v).value());
    }

    @Test
    void interpret_await_async_string() throws Exception {
        /*
         * let r = await (async "hello");
         * r = "hello"
         */
        Value v = RunnerHelper.run("let r = await (async \"hello\"); r");
        assertInstanceOf(StringValue.class, v);
        assertEquals("hello", ((StringValue) v).value());
    }

    @Test
    void interpret_await_named_promise() throws Exception {
        /*
         * let p = async 99;
         * let r = await p;
         * r = 99
         */
        Value v = RunnerHelper.run("""
                let p = async 99;
                let r = await p;
                r
                """);
        assertInstanceOf(IntValue.class, v);
        assertEquals(99L, ((IntValue) v).value());
    }

    @Test
    void interpret_await_async_expression_with_arithmetic() throws Exception {
        /*
         * let r = await (async (3 + 4));
         * r = 7
         */
        Value v = RunnerHelper.run("let r = await (async (3 + 4)); r");
        assertInstanceOf(IntValue.class, v);
        assertEquals(7L, ((IntValue) v).value());
    }

    @Test
    void interpret_await_async_function_call() throws Exception {
        /*
         * let add = (x,y)->x+y;
         * let r   = await (async add(10, 5));
         * r = 15
         */
        Value v = RunnerHelper.run("""
                let add = (x,y)->x+y;
                let r   = await (async add(10,5));
                r
                """);
        assertInstanceOf(IntValue.class, v);
        assertEquals(15L, ((IntValue) v).value());
    }

    @Test
    void interpret_multiple_await_calls() throws Exception {
        /*
         * let a = await (async 10);
         * let b = await (async 20);
         * a + b = 30
         */
        Value v = RunnerHelper.run("""
                let a = await (async 10);
                let b = await (async 20);
                a + b
                """);
        assertInstanceOf(IntValue.class, v);
        assertEquals(30L, ((IntValue) v).value());
    }

    @Test
    void interpret_promise_display_resolves_after_await() throws Exception {
        /*
         * After await the PromiseValue should report "Promise<resolved:...>"
         */
        AST ast = ASTHelper.parser.parse("""
                let p = async 42;
                let r = await p;
                """);
        Value result = RunnerHelper.run(ast);

        // Retrieve the stored PromiseValue from the environment
        org.twelve.gcp.interpreter.OutlineInterpreter interp =
                new org.twelve.gcp.interpreter.OutlineInterpreter(ast.asf());
        interp.runAst(ast);
        PromiseValue pv = (PromiseValue) interp.currentEnv().lookup("p");
        pv.future().get();  // ensure settled
        assertTrue(pv.display().startsWith("Promise<resolved:"),
                "Expected resolved display, got: " + pv.display());
    }

    // =========================================================================
    // Interpretation – callback pattern (.done / .error)
    // =========================================================================

    @Test
    void interpret_then_callback_receives_resolved_value() throws Exception {
        /*
         * let p = async 7;
         * p.then receives 7 via the Java BuiltinMethods API
         */
        AST ast = ASTHelper.parser.parse("let p = async 7;");
        org.twelve.gcp.interpreter.OutlineInterpreter interp =
                new org.twelve.gcp.interpreter.OutlineInterpreter(ast.asf());
        interp.runAst(ast);

        PromiseValue pv = (PromiseValue) interp.currentEnv().lookup("p");
        CompletableFuture<Value> received = new CompletableFuture<>();
        Value thenMethod = BuiltinMethods.promise(pv, "then", interp);
        interp.apply(thenMethod, new FunctionValue(v -> {
            received.complete(v);
            return UnitValue.INSTANCE;
        }));

        Value result = received.get(2, TimeUnit.SECONDS);
        assertInstanceOf(IntValue.class, result);
        assertEquals(7L, ((IntValue) result).value());
    }

    @Test
    void interpret_catch_callback_receives_message_on_failure() throws Exception {
        /*
         * A failed future delivers its error message to the .catch callback.
         */
        CompletableFuture<Value> failed = CompletableFuture.failedFuture(
                new RuntimeException("async failure"));
        PromiseValue pv = new PromiseValue(failed);

        AST ast = ASTHelper.parser.parse("42");  // minimal AST for interpreter context
        org.twelve.gcp.interpreter.OutlineInterpreter interp =
                new org.twelve.gcp.interpreter.OutlineInterpreter(ast.asf());

        CompletableFuture<Value> received = new CompletableFuture<>();
        Value catchMethod = BuiltinMethods.promise(pv, "catch", interp);
        interp.apply(catchMethod, new FunctionValue(v -> {
            received.complete(v);
            return UnitValue.INSTANCE;
        }));

        Value result = received.get(2, TimeUnit.SECONDS);
        assertInstanceOf(StringValue.class, result);
        assertTrue(((StringValue) result).value().contains("async failure"));
    }

    @Test
    void interpret_then_not_triggered_on_failure() throws Exception {
        /*
         * When the promise fails, .then must NOT be invoked.
         */
        CompletableFuture<Value> failed = CompletableFuture.failedFuture(
                new RuntimeException("boom"));
        PromiseValue pv = new PromiseValue(failed);

        AST ast = ASTHelper.parser.parse("42");
        org.twelve.gcp.interpreter.OutlineInterpreter interp =
                new org.twelve.gcp.interpreter.OutlineInterpreter(ast.asf());

        CopyOnWriteArrayList<Value> thenCalls   = new CopyOnWriteArrayList<>();
        CompletableFuture<Value> catchReceived  = new CompletableFuture<>();

        interp.apply(BuiltinMethods.promise(pv, "then",  interp),
                new FunctionValue(v -> { thenCalls.add(v); return UnitValue.INSTANCE; }));
        interp.apply(BuiltinMethods.promise(pv, "catch", interp),
                new FunctionValue(v -> { catchReceived.complete(v); return UnitValue.INSTANCE; }));

        catchReceived.get(2, TimeUnit.SECONDS);
        Thread.sleep(20);
        assertTrue(thenCalls.isEmpty(), ".then must not be called when the promise fails");
    }

    @Test
    void interpret_catch_not_triggered_on_success() throws Exception {
        /*
         * When the promise resolves, .catch must NOT be invoked.
         */
        AST ast = ASTHelper.parser.parse("let p = async 3;");
        org.twelve.gcp.interpreter.OutlineInterpreter interp =
                new org.twelve.gcp.interpreter.OutlineInterpreter(ast.asf());
        interp.runAst(ast);

        PromiseValue pv = (PromiseValue) interp.currentEnv().lookup("p");
        CompletableFuture<Value> thenReceived  = new CompletableFuture<>();
        CopyOnWriteArrayList<Value> catchCalls = new CopyOnWriteArrayList<>();

        interp.apply(BuiltinMethods.promise(pv, "then",  interp),
                new FunctionValue(v -> { thenReceived.complete(v); return UnitValue.INSTANCE; }));
        interp.apply(BuiltinMethods.promise(pv, "catch", interp),
                new FunctionValue(v -> { catchCalls.add(v); return UnitValue.INSTANCE; }));

        Value result = thenReceived.get(2, TimeUnit.SECONDS);
        Thread.sleep(20);
        assertEquals(new IntValue(3L), result);
        assertTrue(catchCalls.isEmpty(), ".catch must not be called on success");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Returns the inferred type of the first let/var binding. */
    private static Outline firstVarType(AST ast) {
        return varType(ast, 0);
    }

    /** Returns the inferred type of the n-th let/var binding (0-based). */
    private static Outline varType(AST ast, int idx) {
        org.twelve.gcp.node.statement.VariableDeclarator decl =
                (org.twelve.gcp.node.statement.VariableDeclarator)
                        ast.program().body().statements().get(idx);
        return decl.assignments().getFirst().lhs().outline();
    }
}
