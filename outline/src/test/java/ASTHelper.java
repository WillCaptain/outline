import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.outline.GCPConverter;
import org.twelve.outline.OutlineParser;

public class ASTHelper {
    public static final OutlineParser parser = new OutlineParser();

    public static AST mockAddFunc() {
        String code = """
                    let add = (x,y)->x+y;
                    let add_2 = (x,y)->{
                    let z = x+y;
                    z
                };""";
        return parser.parse(new ASF(), code);
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
        return parser.parse(new ASF(), code);
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
        return parser.parse(new ASF(), code);
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
        return parser.parse(new ASF(), code);
    }

    public static AST mockDefinedLiteralUnion() {
        String code = """
                module default
                
                var option :100|"some"|String|{name="will"}|("will",30) = 100;
                option++, option = 200;""";
        return parser.parse(new ASF(), code);
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
                let f = fx<a:{gender:"male"|"female",age}>(x:a->String->Int->{name:String,age:Int},y:String,z:Int)->x({gender ="male",age = 30 },y,z);""";

        /*code = """
                let f = <a>(x:a->{name:a,age:Int})->{
                           x("Will").name
                         };""";

         */
        return parser.parse(new ASF(), code);
    }

    
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
        return parser.parse(new ASF(), code);
    }

    public static AST mockArrayDefinition() {
        String code = """
                let a = [1,2,3,4];
                let b: [String] = [];
                let c: [?] = [...5];
                let d = [1...6,2,x->x+"2",x->x%2==0];
                let e = [];""";
        return parser.parse(new ASF(), code);
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
        return parser.parse(new ASF(), code);
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
        return parser.parse(new ASF(), code);
    }

    /**
     * Two-module scenario for import/export outline:
     *   Module shapes: defines outline Summary, creates a sample value and exports it together with a size counter.
     *   Module app:    imports the value (whose type IS the outline structure) + the counter, accesses members,
     *                  and returns (Integer,[String:Number],Integer).
     *
     * Note: export grammar only accepts lowercase IDs, so we export a *value* of the outline type rather
     * than the type name itself.  The importing module receives the full structural type and can access members.
     */
    public static AST mockImportExportOutline() {
        ASF asf = new ASF();
        OutlineParser p = new OutlineParser(new GCPConverter(asf));
        // exporter module: define outline type, create a typed value, export it
        p.parse("""
                module org.twelve.shapes
                outline Summary = { total: Int, data: [String:Number] };
                let sample: Summary = { total = 42, data = ["x":1,"y":2] };
                let n: Int = 10;
                export sample, n as size;
                """);
        // importer module: import the value and counter, access outline members
        return p.parse("""
                module org.twelve.app
                import sample, size from shapes;
                let s = sample;
                let a = s.total;
                let b = s.data;
                let c = size;
                (a, b, c)
                """);
    }

    /**
     * Import-alias test: verifies that "import X as Y" binds the value under Y (not X).
     */
    public static AST mockImportAlias() {
        ASF asf = new ASF();
        OutlineParser p = new OutlineParser(new GCPConverter(asf));
        p.parse("""
                module org.twelve.src
                let val: Int = 99;
                export val, val as copy;
                """);
        return p.parse("""
                module org.twelve.dst
                import val as n, copy from src;
                let a = n;
                let b = copy;
                (a, b)
                """);
    }

    /**
     * Import-outline-type test: exports an outline type (Point) itself from one module,
     * then imports it into another module and extends it (ColorPoint = Point + color).
     * Verifies that inherited members (x, y) and the new member (color) all resolve correctly.
     */
    public static AST mockImportOutlineType() {
        ASF asf = new ASF();
        OutlineParser p = new OutlineParser(new GCPConverter(asf));
        p.parse("""
                module org.twelve.shapes
                outline Point = { x: Number, y: Number };
                let zero: Int = 0;
                export Point, zero;
                """);
        return p.parse("""
                module org.twelve.geo
                import Point, zero from shapes;
                outline ColorPoint = Point { color: String };
                let p: ColorPoint = { x = 1, y = 2, color = "red" };
                let cx = p.x;
                let cc = p.color;
                let z = zero;
                (cx, cc, z)
                """);
    }

    public static ASF educationAndHuman() {
        ASF asf = new ASF();
        OutlineParser parser = new OutlineParser(new GCPConverter(asf));
        parser.parse("""
                module org.twelve.education
                let grade: Int = 1, school: String = "NO.1";
                export grade, school as college;""");
        parser.parse("""
                module org.twelve.human
                import grade as level, college as school from education;
                let age: Int, name: String = "Will", height: Double = 1.68, grade: Int = level;
                export height as stature, name;""");
        return asf;
    }

    public static AST declaredAssignmentTypeMismatch() {
        String code = """
                module me
                var age: Int = "some";""";
        return parser.parse(new ASF(), code);
    }

    public static AST assignMismatch() {
        String code = """
                module me
                var age = "some";
                age = 100;""";
        return parser.parse(new ASF(), code);
    }

    public static AST declaredPolyMismatchAssignment() {
        String code = """
                module me
                var age = "some"&(100|200);
                age = 100.0;""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockErrorAssignOnDefinedPoly() {
        String code = """
                module default
                
                var poly = 100&"some"&{age=40,name="Will"}&(40,"Will");
                poly = 10.0f;
                let poly = 10.0f;""";
        return parser.parse(new ASF(), code);
    }

    public static AST mockAssignOnDefinedLiteralUnion() {
        String code = """
                module default
                
                var option :100|"some"|String|{name="will"}|("will",30) = 100;
                option++, option = 200;
                option = 100;
                option = "some"; """;
        return parser.parse(new ASF(), code);

    }

    public static AST mockOverrideAddFunc() {
        String code = """
                var add = ((x,y)->x+y) & ((x,y,z)->x+y+z);""";
        code = """
                var add = (x,y)->x+y &&& (x,y,z)->{x+y+z};""";
        return parser.parse(new ASF(), code);
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
        return parser.parse(new ASF(), code);
    }

    
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

    /** Number built-in methods: abs/ceil/floor/round/to_int/to_float/sqrt/pow/min/max */
    public static AST mockNumberMethods() {
        String code = """
                let x = 100;
                let a = x.abs();
                let b = x.ceil();
                let c = x.floor();
                let d = x.round();
                let e = x.to_int();
                let f = x.to_float();
                let g = x.sqrt();
                let h = x.pow(2.0);
                """;
        return parser.parse(new ASF(), code);
    }

    /** String built-in methods: length/trim/to_upper/to_lower/split/contains/starts_with/ends_with/index_of/sub_str/replace/to_int/to_number/chars/repeat */
    public static AST mockStringMethods() {
        String code = """
                let s = "hello";
                let a = s.len();
                let b = s.trim();
                let c = s.to_upper();
                let d = s.to_lower();
                let e = s.split(",");
                let f = s.contains("ell");
                let g = s.starts_with("h");
                let h = s.ends_with("o");
                let i = s.index_of("ll");
                let j = s.sub_str(1,3);
                let k = s.replace("l","r");
                let l = s.to_int();
                let m = s.to_number();
                let n = s.chars();
                let o = s.repeat(3);
                """;
        return parser.parse(new ASF(), code);
    }

    /** Bool built-in methods: not/and_also/or_else */
    public static AST mockBoolMethods() {
        String code = """
                let b = true;
                let a = b.not();
                let c = b.and_also(false);
                let d = b.or_else(false);
                """;
        return parser.parse(new ASF(), code);
    }

    /** Array built-in methods: len/reverse/take/drop/filter/forEach/any/all/find/sort/flat_map/min/max */
    public static AST mockArrayMethods() {
        String code = """
                let x = [1,2,3];
                let a = x.len();
                let b = x.reverse();
                let c = x.take(2);
                let d = x.drop(1);
                let e = x.filter(i->i>0);
                x.forEach(i->i.to_str());
                let g = x.any(i->i>0);
                let h = x.all(i->i>0);
                let k = x.find(i->i>0);
                let m = x.sort(a->b->a-b);
                let n = x.flat_map(i->[i]);
                let p = x.min();
                let q = x.max();
                """;
        return parser.parse(new ASF(), code);
    }

    /** Dict built-in methods: length/keys/values/contains_key/get */
    public static AST mockDictMethods() {
        String code = """
                let d = ["a":1,"b":2];
                let a = d.len();
                let b = d.keys();
                let c = d.values();
                let e = d.contains_key("a");
                let f = d.get("a");
                """;
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
                let f = fx<a>(person,gender:a)-> person{gender = gender};
                let g = f<String>;
                g("Will",1);
                f({name="Will"},1);
                """;
        return parser.parse(new ASF(), code);
    }

    public static AST mockSelfRecursive() {
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
                let g = fx<a,b>()->{
                  z: a = 100,
                  f = fx<c>(x: b)->(y: c)->y
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

    public static AST mockFutureReferenceFromEntity() {
        String code = """
                let animal = {
                    walk = ()-> this,
                    age = 40,
                    me = this
                };
                let person = animal {
                    talk = ()->this,
                    name = "Will",
                    gender = Male
                };
                let create_a = c->{
                    c = c,
                    me = this,
                    him = ()->{
                        return {
                            me = this,
                            name = c
                        };
                    },
                    get_me  = ()->this
                };
                let b = {
                    a = create_a(123),
                    d = "Test"
                };
                let a = create_a("aaa");
                (person.walk().talk().name,person.talk().walk().name, a.get_me().c, a.me.c,person.me.age,person.me.gender,b.d,b.a.c,b.a.d,a.him().me.name)
                """;
        return new OutlineParser().parse(new ASF(), code);
    }

    public static AST mockFutureReferenceFromOutline() {
        String code = """
                
                //system
                outline Aggregator = <a>{
                      count: Unit -> ~this,
                      sum: (a -> Number) -> ~this,
                      avg: (a -> Number) -> ~this,
                      min: (a -> Number) -> ~this,
                      max: (a -> Number) -> ~this,
                      compute: Unit -> [String:Number]
                  };
                
                  outline GroupBy = <k, a>{
                      filter: (a -> Bool) -> ~this,
                      count: Unit -> [k:Int],
                      aggregate: <b>(Aggregator<a> -> b) -> [k:b],
                      to_map: Unit -> [k:VirtualSet<a>]
                  };
                
                outline VirtualSet = <a>{
                    //non-terminal operators
                    filter: (a->Bool) -> ~this,
                    order_by: (a -> ?) -> ~this,
                    take: Int -> Int -> ~this,
                    map: fx<b> (a->b) -> VirtualSet<b>,
                    type:#"me",
                
                     //terminal operators
                     first: Unit -> a,
                     last: Unit -> a,
                     count: Unit -> Int,
                     exists: Unit -> Bool,
                     sum: (a->Number) -> Number,
                     avg: (a->Number) -> Number,
                     min: fx<b>(a -> b) -> b,
                     max: fx<b>(a -> b) -> b,
                     reduce: fx<b>b->(a->b)->b,
                     for_each: (a -> Unit) -> Unit,
                     aggregate: <b>(Aggregator<a> -> b) -> b,
                     group_by: <b>(a->b)->GroupBy<b,a>
                };
                
                //ontology
                outline Employee = {
                  //attributes
                  id: String,
                  name: Name,
                  age: Int,
                  birthday: Date,
                  gender: Gender,
                
                  //edges
                  report_to: Unit -> Employee,
                  is_reported_by:Unit -> Employees,
                  live_in: Unit -> Address,
                
                  //single or both actions
                  send_birthday_greeting: String -> Unit,
                  action_both: Unit -> String
                };
                
                outline Employees  = VirtualSet<Employee>{
                  //edges
                  report_to: Unit-> Employees,//edge
                  is_reported_by: Unit->Employees,
                
                  //both actions
                  action_both: Unit->VirtualSet<String> //action for single and both
                };
                
                let employees = __ontology_repo__<Employees>;
                let employee = __ontology_mem__<Employee>;
                let count_1 = employees
                    .filter(e->e.age>30)
                    .is_reported_by()
                    .count();
                
                let agg = employees
                    .aggregate(agg->{
                        agg
                        .count()
                        .avg(e->e.age)
                        .compute()
                    });
                
                let count_2 = employee.is_reported_by().count();
                (count_1, agg, count_2)
                """;
        return parser.parse(new ASF(), code);
    }

    public static AST mockReferCallLazy(){
        String code = """
               outline A = <c,e>{
                b:B<String,e>,
                c:c,
                e:e,
                g:<d>(c->d)->A<d,e>,
                t:~this
               };
               outline B = <e>A<e>{
                d:e
               };
               
               let fb = <a>(a:a)->a;
               let fa = <a>(a:a)->{
                return {
                    a = a,
                    b = fb<a>
                };
               };
               
               
               let a = __sys__<B<Int,Int>>;
               let c_1 = a.b.c;
               let c_2 = a.t.c;
               let d_1 = a.b.d;
               let d_2 = a.t.d;
               let g_1 = a.b.g(x->x).c;
               let g_2 = a.t.g(x->"will").c;
               let f = fa(100);
               
               (c_1,c_2,d_1,d_2,g_1,g_2,f.a,f.b(10),f.b("some"))
               
               """;
//        code = """
//           let fb = <a>(a:a)->a;
//           let fa = <a>(a:a)->{
//            return {
//                a = a,
//                b = fb<a>
//            };
//           };
//           fa(100).b(100)
//            """;
        return parser.parse(new ASF(), code);
    }

    public static AST mockInterInvoke() {
        String code = """
               outline Blood = A|B|C;
               outline Region = Asia|Africa|America;
               
               let create_wife = husband ->{
                    return{ 
                        husband = husband,
                        family_name = husband.family_name
                    };
               };
               
               let create_son = origin->{
                    return {
                        age = 20,
                        origin = origin,
                        myself = this,
                        father = create_father(this)
                    };
               };
               
               let create_father = son->{
                    return {
                        name = "father",
                        family_name = "Zhang",
                        son = son,
                        wife = create_wife(this)
                    };
               };
               let son = create_son(Asia);
               
               outline Son = <a>{
                    name: String,
                    origin: a,
                    father: Father<a>
               };
               outline Father = <b>{
                    age: Int,
                    origin: b,
                    son: Son<b>
               };
               let father = __constructor__<Father<Blood>>;
         
              (son.origin, son.father.name, son.father.son.age, son.father.son.origin, son.father.wife.family_name,father.son.name, father.son.father.origin, father.son.origin)
               """;

        return parser.parse(new ASF(), code);
    }

    public static AST mockMoreReference() {
        String code = """
                outline O1 = fx<a> a->a;
                outline O11 = fx<a> a->a;
                
                outline O2 = <a,b>{name:a,age:b};
                outline O3 = <a,b>(a,b);
                outline O4 = O1<String>;
                outline O5 = O2<String,Int>;
                outline O6 = O3<Int,String>;
                """;
        return parser.parse(new ASF(), code);
    }

    public static AST mockReferCallThis() {
        String code = """
                let mono = fx<a>(a:a)->{
                    map = <b>(x:(a->b))->{
                        return builder
                    }
                };
                """;
        return parser.parse(new ASF(), code);
    }

    public static AST mockTypeSelfReturn() {
        String code = """
                outline Aggregator = <a>{
                      count: Unit -> ~this,
                      avg: (a -> Number) -> ~this,
                      sum: (a->Number) -> ~this,
                      compute: Unit -> [String:Number]
                  };
                
                outline VirtualSet = <a>{
                     aggregate: <b>(Aggregator<a> -> b) -> b
                };

                outline Employee = {
                  name: String,
                  age: Int
                };
 
                let employees = __ontology_repo__<VirtualSet<Employee>>;


                let agg = employees
                    .aggregate(agg->{
                        agg.avg(e->e.age).avg(e->e.name).compute()//avg(e->e.name) error seems hidden by the first avg: .avg(e->e.age)
                    });
                agg
                """;
        return parser.parse(new ASF(), code);
    }

    public static AST literalOutline() {
        String code = """
                outline Gender = Male(String,Int)|Female{name:String, age:Int};
                outline Human = {
                      gender: Gender,
                      specie: #"human" //literal type,means specie can only be "human" String
                  };
                let person:Human = {
                    gender = Male("Will",49)
                }; // don't need to set specie which is literal type: "human"
                
                person.specie // the value should be "human"
                  
                """;
        return parser.parse(new ASF(), code);

    }
}