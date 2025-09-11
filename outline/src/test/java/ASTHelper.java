import lombok.SneakyThrows;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.common.SELECTION_TYPE;
import org.twelve.outline.OutlineParser;

import java.io.IOException;

public class ASTHelper {
    private static OutlineParser parser;

    static {
        try {
            parser = new OutlineParser();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static AST mockAddFunc() {
        String code = """
                    let add = (x,y)->x+y;
                    let add_2 = (x,y)->{
                    let z = x+y;
                    z
                };""";
        return parser.parse(code);
    }

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
        return parser.parse(code);
    }

    public static AST mockSimpleTuple() {
        String code = """
                module default
                
                let person = ("Will",()->this.0);
                let name_1 = person.0;
                let name_2 = person.1();""";
        return parser.parse(code);
    }

    public static AST mockGenericTupleProjection() {
        String code = """
                module default
                
                let f = (x: (?, ?))->(x.1,x.0);
                let h = f(("Will",30));
                let will = h.1;
                let age = h.0;""";
        return parser.parse(code);
    }

    public static AST mockTupleProjection() {
        String code = """
                module default
                
                let f =fx<a,b>(x: a)->(y: ((a, String), b))->y;
                let h = f(100,(("Will","Zhang"),"male"));
                let g = f("Will",(("Will","Zhang"),30));
                let will = g.0.0;
                let age = g.1;""";
        return parser.parse(code);
    }

    public static AST mockDefinedPoly() {
        String code = """
                module default
                
                var poly = 100&"some"&{age=40,name="Will"}&(40,"Will");""";
        return parser.parse(code);
    }

    public static AST mockDefinedLiteralUnion() {
        String code = """
                module default
                
                var option :100|"some"|String|{name="will"}|("will",30) = 100;
                option++, option = 200;""";
        return parser.parse(code);
    }

    public static AST mockIf(SELECTION_TYPE selectionType) {
        String code;
        if(selectionType== SELECTION_TYPE.IF) {
            code = """
                    module default
                    
                    if(name=="Will"){
                      name
                    } else if(name=="Evan"){
                      age
                    } else {
                      "Someone"
                    }""";
        }else{
            code = """
                    module default
                    
                    let n = name=="Will"? name: "Someone";""";
        }
        return parser.parse(code);
    }

    public static AST mockDeclare() {
        String code = """
         let f = <a:{gender:"male"|"female",age}>(x:a->String->Int->{name:String,age:Int},y:String,z:Int)->x({gender ="male",age = 30 },y,z);""";
        return parser.parse(code);
    }

    @SneakyThrows
    public static AST mockReferenceInFunction() {
        String code = """
                let f = fx<a,b>(x: a)->{
                  let y: b = 100;
                  y
                };""";
        return parser.parse(code);
    }

    public static AST mockAs() {
        String code = """
                let a = {
                  name = "Will",
                  age = 20
                } as {name: String};
                let b = {
                  name = "Will",
                  age = 20
                } as {name: Int};""";
        return parser.parse(code);
    }

    public static AST mockArrayDefinition() {
        String code = """
                let a = [1,2,3,4];
                let b: [String] = [];
                let c: [?] = [...5];
                let d = [1...6,2,x->x+"2",x->x%2==0];
                let e = [];""";
        return parser.parse(code);
    }

    public static AST mockDictDefinition() {
        String code = """
                let a = [{
                  name = "Will"
                }:"Male", {
                  name = "Ivy",
                  age = 20
                }:"Female"];
                let b: [Int : String] = [:];
                let c = [:];
                let d: [String : ?] = ["Will":30, 30:30];
                let e: [? : ?] = ["Male":0];""";


        /*String code = """
                let a = [{
                  name = "Will"
                }:"Male"];""";*/
        return parser.parse(code);
    }

    public static AST mockArrayAsArgument() {
        String code = """
                let f = x->x[0];
                let g = (x: [?])->i->{
                  let y = x[i];
                  x = ["will","zhang"];
                  y
                };
                let r = fx<a>(x: [a])->{
                  var b = [1,2];
                  b = x;
                  let c: a = x[0];
                  c
                };
                f([{
                  name = "Will"
                }]);
                f(100);
                g(["a","b"],0);
                g([1],"idx");
                let r1 = r<Int>;
                r1([1,2]);
                let r2 = r<String>;
                r([1,2]);""";


//        String code = """
//                {name="Will"};
//                10""";
        return parser.parse(code);
    }

    public static AST mockGeneral() {
        String code = """
                module org.twelve.human
                import grade as level, college as school from education;
                import * from e.f.g;
                let age: Int = 10, name = "Will", height: Double = 1.68, grade = level;
                export height as stature, name;""";
        return parser.parse(code);
    }
}