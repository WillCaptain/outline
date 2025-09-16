import lombok.SneakyThrows;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.common.SELECTION_TYPE;
import org.twelve.outline.GCPConverter;
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

    public static AST mockRandomPersonEntity() {
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

    public static AST mockSimplePersonEntity(){
        String code = """
                let person = {
                    name = "Will",
                    get_name = ()->this.name,
                    get_my_name = ()->name
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

    @SneakyThrows
    public static ASF educationAndHuman() {
        ASF asf = new ASF();
        OutlineParser parser = new OutlineParser(new GCPConverter(asf));
        String code = """
                module org.twelve.education
                let grade: Int = 1, school: String = "NO.1";
                export grade, school as college;""";
        parser.parse(code);
        code = """
                module org.twelve.human
                import grade as level, college as school from education;
                let age: Int, name: String = "Will", height: Double = 1.68, grade: Int = level;
                export height as stature, name;""";
        parser.parse(code);
        return asf;
    }

    public static AST declaredAssignmentTypeMismatch() {
        String code = """
                module me
                var age: Int = "some";""";
        return parser.parse(code);
    }

    public static AST assignMismatch() {
        String code = """
                module me
                var age = "some";
                age = 100;""";
        return parser.parse(code);
    }

    public static AST declaredPolyMismatchAssignment() {
        String code = """
                module me
                var age = "some"&(100|200);
                age = 100.0;""";
        return parser.parse(code);
    }

    public static AST mockErrorAssignOnDefinedPoly() {
        String code = """
                module default
                
                var poly = 100&"some"&{age=40,name="Will"}&(40,"Will");
                poly = 10.0f;
                let poly = 10.0f;""";
        return parser.parse(code);
    }

    public static AST mockAssignOnDefinedLiteralUnion() {
        String code = """
                module default
                
                var option :100|"some"|String|{name="will"}|("will",30) = 100;
                option++, option = 200;
                option = 100;
                option = "some"; """;
        return parser.parse(code);

    }

    public static AST mockOverrideAddFunc() {
        String code = """
                var add = ((x,y)->x+y) & ((x,y,z)->x+y+z);""";
        return parser.parse(code);
    }

    public static AST mockGCPTestAst() {
        String code = """
                module test
                let a = 100.0, b = 100, c = "some";
                a+b;
                a+c;
                a-b;
                a-c;
                a==b;""";
        return parser.parse(code);
    }

    @SneakyThrows
    public static AST mockSimplePersonEntityWithOverrideMember() {
        String code = """ 
                module test
                let person = {
                    get_name = ()->this.name,
                    name = "Will",
                    var get_name = (()->this.name)&(last_name->this.name+last_name)
                    //var get_name = (()->this.name)&(last_name->last_name)
                };
                let name_1 = person.name;
                let name_2 = person.get_name();""";
        return parser.parse(new ASF(),code);
    }

    public static AST mockInheritedPersonEntity() {
        String code = """
                let person = {
                  get_name = ()->this.name,
                  get_my_name = ()->name,
                  name = "Will"
                };
                let name_1 = person.name;
                let name_2 = person.get_name();
                let me = person{
                  get_full_name = ()->baseNode.name+"Zhang",
                  var get_name = ()->"Will Zhang"
                };""";;
        return parser.parse(new ASF(),code);
    }
}