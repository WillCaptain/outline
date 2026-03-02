import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import static org.junit.jupiter.api.Assertions.*;

public class LTTest {
    @Test
    void test_lt_standalone() {
        // Just the expression - should work
        AST ast = RunnerHelper.parse("x -> x < 3");
        System.out.println("E1: " + ast.errors());
        assertTrue(ast.errors().isEmpty(), "x -> x < 3: " + ast.errors());
    }
    
    @Test
    void test_lt_in_var() {
        AST ast = RunnerHelper.parse("let f = x -> x < 3; f");
        System.out.println("E2: " + ast.errors());
        assertTrue(ast.errors().isEmpty(), "let f = x->x<3: " + ast.errors());
    }
    
    @Test
    void test_assignment_rhs_lambda() {
        AST ast = RunnerHelper.parse("let f = x -> 1; f");
        System.out.println("E3: " + ast.errors());
        assertTrue(ast.errors().isEmpty(), "let f = x->1: " + ast.errors());
    }
    
    @Test
    void test_lt_in_call() {
        AST ast = RunnerHelper.parse("[1,2].filter(x -> x < 3)");
        System.out.println("E4: " + ast.errors());
        assertTrue(ast.errors().isEmpty(), "filter(x->x<3): " + ast.errors());
    }

    @Test
    void test_gt_in_call() {
        AST ast = RunnerHelper.parse("[1,2].filter(x -> x > 3)");
        System.out.println("E5: " + ast.errors());
        assertTrue(ast.errors().isEmpty(), "filter(x->x>3): " + ast.errors());
    }
}
