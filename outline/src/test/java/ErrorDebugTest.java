import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.GCPConverter;
import org.twelve.outline.OutlineParser;

public class ErrorDebugTest {
    private static final OutlineParser parser = new OutlineParser(new GCPConverter());

    @Test
    void print_inherited_this_errors() {
        AST ast = ASTHelper.mockInheritedThis();
        ast.asf().infer();
        System.out.println("=== ERRORS (" + ast.errors().size() + ") ===");
        for (var e : ast.errors()) {
            System.out.println("  code=" + e.errorCode() + 
                " | msg=" + e.message() + 
                " | node=" + e.node().getClass().getSimpleName() +
                " | lexeme='" + e.node().lexeme().substring(0, Math.min(20, e.node().lexeme().length())) + "'");
        }
    }

    @Test
    void print_shape_errors() {
        ASF asf = new ASF();
        AST ast = new OutlineParser().parse(asf, """
outline Shape = Circle { r: Int }
              | Rect   { w: Int, h: Int }
              | Triangle { base: Int, height: Int }
              | Dot;

let area = s -> match s {
    Circle   { r }              -> r * r,
    Rect     { w, h }           -> w * h,
    Triangle { base, height }   -> (base * height) / 2,
    Dot                         -> 0 ,
    _ -> 0
};

let shapes = [Circle{r=5}, Rect{w=4, h=6}, Circle{r=15}, Dot];
let areas  = shapes.map(s -> area(s));
area(Rect{w=4, h=6});
""");
        asf.infer();
        System.out.println("=== SHAPE ERRORS (" + ast.errors().size() + ") ===");
        for (var e : ast.errors()) {
            var n = e.node();
            String px = n.parent() == null ? "null" : n.parent().getClass().getSimpleName()
                    + "('" + n.parent().lexeme().substring(0, Math.min(30, n.parent().lexeme().length())) + "')";
            System.out.println("  code=" + e.errorCode() + " | msg=" + e.message()
                + " | node=" + n.getClass().getSimpleName()
                + " | lexeme='" + n.lexeme().substring(0, Math.min(40, n.lexeme().length())) + "'"
                + " | outline=" + n.outline()
                + " | parent=" + px);
        }
    }

}
