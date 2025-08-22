import lombok.SneakyThrows;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.OutlineParser;

public class ASTHelper {
    @SneakyThrows
    public static AST mockAddFunc() {
        String code = """
                    let add = (x,y)->x+y;
                    let add_2 = (x,y)->{
                    let z = x+y;
                    z
                };""";
        return new OutlineParser().parse(code);
    }
}
