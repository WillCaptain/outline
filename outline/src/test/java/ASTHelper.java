import lombok.SneakyThrows;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
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

    public static AST mockSimplePersonEntity() {
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
                let person = ("Will",()->this.0,40);
                let name_1 = person.0;
                let name_2 = person.1();
                outline Human = (String,...,Int);
                """;
        return parser.parse(new ASF(), code);
    }

    public static AST mockGenericTupleProjection() {
        String code = """
                let f = (x: (?, ?))->(x.1,x.0);
                let h = f(("Will",30));
                let will = h.1;
                let age = h.0;""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockTupleProjection() {
        String code = """
                let f =fx<a,b>(x: a)->(y: ((a, String), b))->y;
                let h = f(100,(("Will","Zhang"),"male"));
                let g = f("Will",(("Will","Zhang"),30));
                let will = g.0.0;
                let age = g.1;""";
        return parser.parse(new ASF(), code);
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

    public static AST mockMatch() {
        String code = """
                let num = 10;
                let ent = {name = {last="Will",first="Zhang"}, age = 48};
                let tpl = (("Will","Zhang"),48);
                
                let converted = match num{
                    m if m>9  -> m,
                    8 -> 7,
                    _ -> "str"
                };
                let name_1 = match tpl {
                    (name,age) if age>40 -> name.0,
                    ((last,String),...) -> last
                };
                
                let name_2 = match ent {
                    {name,age} if age>40 -> name.last,
                    {name:{last}, age} -> last+age,
                    _ -> (ent.age, ent.name.last)
                };
                
                """;
        return parser.parse(new ASF(), code);
    }

    public static AST mockIf(boolean isIf) {
        String code;
        if (isIf) {
            code = """
                    module default
                    
                    if(name=="Will"){
                      name
                    } else if(name=="Evan"){
                      age
                    } else {
                      "Someone"
                    }""";
        } else {
            code = """
                    module default
                    
                    let n = name=="Will"? name: "Someone";""";
        }
        return parser.parse(new ASF(), code);
    }

    public static AST mockIf() {
        String code = """
                let name = "Will";
                let age = 30;
                
                let get = () ->{
                    if(age is Int as a){
                        (name,a)
                    }
                    if(name=="Will"){
                      name
                    } else if(name=="Evan"){
                      age
                    } else {
                      "Someone"
                    }
                };
                get()""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockDeclare() {
        String code = """
                let f = <a:{gender:"male"|"female",age}>(x:a->String->Int->{name:String,age:Int},y:String,z:Int)->x({gender ="male",age = 30 },y,z);""";

        /*code = """
                let f = <a>(x:a->{name:a,age:Int})->{
                           x("Will").name
                         };""";

         */
        return parser.parse(new ASF(), code);
    }

    @SneakyThrows
    public static AST mockReferenceInFunction() {
        String code = """
                let f = fx<a,b>(x: a)->{
                  let y: b = 100;
                  y
                };""";
        return parser.parse(new ASF(), code);
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
        return parser.parse(new ASF(), code);
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
        code = """
                var add = (x,y)->x+y &&& (x,y,z)->{x+y+z};""";
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
        return parser.parse(new ASF(), code);
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
                };""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockInheritedPersonEntityWithOverrideMember() {

        String code = """
                let person = {
                     get_name = ()->this.name,
                     get_my_name = ()->name,
                     name = "Will"
                };
                let name_1 = person.name;
                let name_2 = person.get_name();
                let me = person{
                    get_name = last_name->{
                        this.get_name()+last_name;
                        100
                    },
                    get_other_name = ()->{
                        get_name();
                        get_name("other")
                    }
                };
                me.get_name("Zhang");""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockOptionIsAs() {
        String code = """
                let result = {
                    var some: String|Int = "string";
                    if(some is Int){
                        some
                    } else if(some is String as str){
                        str
                    } else {
                        100
                    }
                };""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockPolyIsAs() {
        String code = """
                let result = {
                    var some = 100&"string";
                    let int = some as Int;
                    let str = some as String;
                    if(some is Int){
                       some
                    }else if(some is String as str){
                        str
                    }else{100}
                };""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockGenericIsAs() {
        String code = """
                let result = ((some:Int)->{
                    if(some is Int){
                         some
                    }else if(some is String as str){
                        str
                    }else{100}
                })(100);""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockReferenceInEntity() {
        String code = """
                let g = fx<a,b>()->{
                  {
                    z: a = 100,
                    f = fx<c>(x: b)->(y: c)->y
                  }
                };""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockBasicAdtMethods() {
        String code = """
                let x = 100;
                x.to_str();
                let s = "str";
                s.to_str();
                let b = true;
                b.to_str();""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockArrayMapMethod() {
        String code = """
                let x = [1,2];
                let y = x.map(i->i.to_str());
                y[0]""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockArrayReduceMethod() {
        String code = """
                let x = [1,2];
                let y = x.reduce((acc,i)->acc+i,0.1);
                y""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockGcpDeclareToBe() {
        String code = """
                let f = (x:Int)->x;
                f("some");
                f(100);""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockGcpExtendToBe() {
        String code = """
                let f = x->{
                  x = 10;
                  x
                };
                f("some");
                f(100);""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockGcpHasToBe() {
        String code = """
                let f = x->{
                    var y = "str";
                    y = x;
                    x
                };
                f("some");
                f(100);""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockGcpDefinedToBe() {
        String code = """
                let f = x->x+1;
                f("some");
                f(100);""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockGcpAdd() {
        String code = """
                let f = x->y->x+y;
                f("some",10);
                f(10,10.0f);
                let z = f("some");
                z("people");""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockGenericReferEachOther() {
        String code = """
                let f = (x,y,z)->{
                    y = x;
                    z = y;
                    x+y+z
                };
                f("some","people",10.0f);
                f(10,10,10);""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockGcpHofProjection1() {
        String code = """
                let f = (x,y)->y(x);
                f(10,x->x*5);""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockGcpHofProjection2() {
        String code = """
                let f = (y,x)->y(x);
                f(x->x*5,"10");""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockGcpHofProjection3() {
        String code = """
                let f = (x,y,z)->z(y(x));
                f(10,x->x+"some",y->y+100);""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockGcpHofProjection4() {
        String code = """
                let f = (z,y,x)->z(y(x));
                f(y->y+100,x->x,10);""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockDeclaredHofProjection() {
        String code = """
                let f = fx<a>(x:a->{name:a,age:Int})->{
                    x("Will").name
                };
                f<Int>;
                f(n->{name=n,age=30})""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockExtendHofProjection() {
        String code = """
                let f = x->{
                    x = a->{name=a};
                    x("Will").name
                };
                f(n->{name=n})""";
        return parser.parse(new ASF(), code);
    }

    private static String mockEntityProjection_(int i, String append) {
        String arg1 = "x,y,z";
        if (i == 1) arg1 = "x,z,y";
        if (i == 2) arg1 = "z,x,y";
        String x = "20", y = "{name = \"Will\"}", z = "{combine = (x,y)->{{age = x,name = y.name}}}";
        String arg2 = x + "," + y + "," + z;
        if (i == 1) arg2 = x + "," + z + "," + y;
        if (i == 2) arg2 = z + "," + x + "," + y;
        return String.format("""
                let f = (%s)->%s;
                f(%s)""", arg1, append, arg2);

    }

    public static AST mockEntityProjection1(int i) {
        String code = mockEntityProjection_(i, "z.combine(x,y)");
        return parser.parse(new ASF(), code);
    }

    public static AST mockEntityProjection2(int i) {
        String code = mockEntityProjection_(i, "z.combine(x,y).name");
        return parser.parse(new ASF(), code);
    }

    public static AST mockEntityProjection3(int i) {
        String code = mockEntityProjection_(i, "z.combine(x,y).gender");
        return parser.parse(new ASF(), code);
    }

    public static AST mockEntityProjection4(int i) {
        String code = mockEntityProjection_(i, "{\n" +
                "          var w = z;\n" +
                "          w.combine(x,y)\n" +
                "        }");
        return parser.parse(new ASF(), code);
    }

    public static AST mockEntityProjection5(int i) {
        String code = mockEntityProjection_(i, "{\n" +
                "          var w: {combine: Int->{name: Int}->{name: Int}} = z;\n" +
                "          w.combine(x,y)\n" +
                "        }");
        return parser.parse(new ASF(), code);
    }

    public static AST mockExtendEntityProjection() {
        String code = """
                let f = <a>(person,gender:a)-> person{gender = gender};
                let g = f<String>;
                g("Will",1);
                f({name="Will"},1);
                """;
        return parser.parse(new ASF(), code);
    }

    public static AST mockRecursive() {
        String code = """
                let factorial = n->n==0? 1: factorial(n-1);
                factorial(100);
                factorial("100");""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockComplicatedHofProjection() {
        String code = """
                let f = fx<a>(x: a->a)->y->z->{
                    x = b->b+"some";
                    y = x;
                    x = z;
                    y
                };
                f<String>;
                f<Int>;
                f(n->"some");""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockExtendHastoDefinedProjection() {
        String code = """
                let f = a->x->y->z->{
                  x = a->{
                    name = a,
                    age = 20
                  };
                  y = x;
                  x = z;
                  x(a).name+x(a).age
                };
                let g = f("Will");
                let h = g(a->{
                  name = a,
                  age = 20
                });
                let i = h(a->{
                  name = a,
                  age = 20
                });
                i(a->{
                  name = a,
                  gender = "male"
                })""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockMultiExtendProjection() {
        String code = """
                let f = x->y->{
                  y = "Noble";
                  y = x;
                  y
                };
                let g = f("Will");
                g("Zhang");
                f(20,"Zhang")""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockMultiDefinedProjection() {
        String code = """
                let f = x->{
                  x.name;
                  x.age;
                  x
                };
                f({
                  name = "Will",
                  age = 20,
                  gender = "Male"
                });
                f({
                  name = "Will"
                });""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockDictAsArgument() {
        String code = """
                let f = x->y->x[y];
                let g = (x: [?:?])->i->{
                  let y = x[i];
                  x = ["Will":"Zhang"];
                  y
                };
                let r = fx<a>(x: [String : a])->{
                  let b = ["Will":30];
                  b = x;
                  let c: a = x["Will"];
                  c
                };
                f(["Will":"Zhang"],"Will");
                f(["Will"],0);
                f(["Will":"Zhang"],0);
                g(["Will":"Zhang"],0);
                let r1 = r<Int>;
                r1(["Will":30]);
                let r2 = r<String>;
                r([1:2]);""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockReferenceInEntity1() {
        String code = """
                let g = <a,b>()->{
                  z: a = 100,
                  f = <c>(x: b)->(y: c)->y
                };
                let f1 = g<Int,String>().f;
                let f2 = f1<Long>;""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockUnpack() {
        String code = """
                let tuple = (("Will","Zhang"),"Male",20);
                let ent = {name = {last_name = "Will", first_name = "Zhang"}, gender = "Male", age = 20};
                let ((name,_),gender) = tuple;
                let ((last,first),...,age) = tuple;
                let {name:{last_name}, gender as g} = ent;
                last
                first
                g
                last_name
                name
                gender
                age
                """;
        return parser.parse(new ASF(), code);
    }

    public static AST mockWith() {
        String code = """
                let resource = {
                    create = ()-> {
                        return {
                            open=()->{}, 
                            close=()->{},
                            done=true 
                        };
                    }
                };
                let result = with resource.create() as r{
                    r.done
                };
                result
                """;
        return parser.parse(new ASF(), code);
    }

    public static AST mockSymbolMatch() {
        String code = """
                outline Human = Male{name:String, age:Int}|Female(Int, String), Pet = Dog|Cat;
                let will = Male{name="will",age=40};
                let ivy = Female("ivy",40);
                let pet = Dog;
                let get_name = someone->{
                    match someone {
                        Male{name} -> name,
                        {name} -> name,
                        (_,age) -> age,
                        Female(_,age) ->age,
                        _ -> {other=100f}
                    }
                };
                (get_name(will),get_name(ivy),get_name(pet))
                """;
        return parser.parse(new ASF(), code);
    }
}