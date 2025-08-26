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

    @SneakyThrows
    public static AST mockSimplePersonEntity() {
        String code = """
                module test
                let person = {
                  var get_name = ()->this.name.a(x,y).b.c.to_str(),
                  get_my_name = ()->name,
                  name = "Will"
                };
                let name_1 = person.name;
                let name_2 = person.get_name();""";
        return new OutlineParser().parse(code);
    }

    @SneakyThrows
    public static AST mockSimpleTuple() {
        String code = """
                module default
                
                let person = ("Will",()->this.0);
                let name_1 = person.0;
                let name_2 = person.1();""";
        return new OutlineParser().parse(code);
    }

    @SneakyThrows
    public static AST mockGenericTupleProjection() {
        String code = """
                module default
                
                let f = (x: (?, ?))->(x.1,x.0);
                let h = f(("Will",30));
                let will = h.1;
                let age = h.0;""";
        return new OutlineParser().parse(code);
    }
}
//this.name.a(x,y).b.c.to_str()