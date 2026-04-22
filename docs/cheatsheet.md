# Outline Cheat Sheet

One page. Everything you need to write real Outline. Each section links to
deeper docs; drill down only when needed.

> **Rule of thumb.** You almost never need a type annotation. When you add one,
> treat it as a *constraint* — a downstream misuse will surface as an inference
> error at the use site, not here.

---

## 1. Bindings

```outline
let x  = 1;                  // immutable, inferred Int
let y: String = "hi";        // optional annotation — acts as a constraint
var z  = 0;  z = z + 1;      // mutable (re-assignable)
```

## 2. Literals

```outline
42        -7        0           // Int
100L                            // Long
3.14      1.0e10                // Double
1.5f                            // Float
"hello"                         // String
true      false                 // Bool
null                            // Nothing   (bottom of any T?)
unit                            // Unit
```

Underscore separators: `1_000_000`. Trailing commas in records/lists are **rejected**.

## 3. Records (structural, no declaration needed)

```outline
let p = { name = "Will", age = 10 };      // Outline style
let q = { name:  "Will", age:  10 };      // JS style (identical)

p.name                                     // "Will"
p{ age = 11 }                              // copy constructor — new record, same type
```

Inside a record, methods see `this`:

```outline
let vec = {
  x = 3, y = 4,
  move_x = (dx: Int) -> this{ x = x + dx },
  len2   = () -> this.x * this.x + this.y * this.y
};
vec.move_x(1).len2();                      // 32, chain keeps Vec's type
```

## 4. Tuples, lists, dicts

```outline
let pt = (10, 20);             pt.0              // 10
let xs = [1, 2, 3];            xs[0]             // 1
let d  = ["name": "Alice"];    d["name"]         // "Alice"
```

## 5. Functions — lambdas and `fx`

```outline
let inc   = x -> x + 1;                   // single param: no parens needed
let add   = (a, b) -> a + b;              // multi-param
let curry = x -> y -> x + y;              // curried: every function is curried
let typed = (x: Int): Int -> x * x;       // annotated arg + return (both optional)

fx greet(name: String): String {          // named function = sugar for `let greet = ...`
  "Hello, " + name
};
// fx enables recursion: the body may reference `greet` itself.
```

Blocks are expressions; the last expression is the value:

```outline
let f = x -> {
  let sq = x * x;
  sq + 1                      // no trailing `;` → this is the return value
};
```

## 6. Control flow — everything returns a value

```outline
let label = if (age >= 18) "adult" else "minor";

let kind = match p {
  { name = "root" }       -> "admin",
  { age = a } if a >= 18  -> "adult",
  _                       -> "other"
};

if (v is Employee) (v as Employee).name else "unknown"
```

## 7. Type declarations (`outline`)

```outline
outline Person = { name: String, age: Int };          // record
outline Result = Ok | Err;                             // sum (constant variants)
outline Box    = <a> { value: a };                     // generic
outline Shape  = Circle{r:Int} | Rect{w:Int,h:Int};    // sum with per-variant structure
```

### Structural ADTs (tagged variants)

```outline
outline Status = Pending{ name: String } | Approved{ boss: String };

let a = Pending{ name = "Will" };                      // free literal (just a tagged record)
let s: Status        = Pending{ name = "Will" };       // upcast at the binding
let q = Status.Approved{ boss = "Will" };              // qualified upcast (eager check)

let show = x: Status -> match x {
  Pending { name as n }  -> n,
  Approved{ boss as n }  -> n
};
```

- Extra fields are **allowed** (row polymorphism): `Pending{ name, extra }` still belongs to `Status`.
- Tag-only variants: `outline Maybe = Just{v:Int} | None;` then `Maybe.None` is legal.
- Error text phrases the failure as *belonging*, not *field missing*:
  `literal does not belong to 'Approved' of Status: missing 'boss'`.

## 8. Generics & self-type chaining (`this`)

```outline
outline Stream = <a> {
  data:   [a],
  filter: (p: a -> Bool)  -> this{ data = data.filter(p) },
  map:    <b>(f: a -> b) -> this{ data = data.map(f) }
};

Stream{ data = [1,2,3,4,5] }
  .filter(x -> x % 2 == 0)      // Stream<Int>
  .map(x -> x * x)              // still Stream<Int>
  .data;                         // [4, 16]
```

Inside a record, `this` is the concrete receiver, not the declaring outline.
Subtypes keep their methods across chained calls.

## 9. Literal types & defaults

```outline
outline ApiKey = {
  key:    String,
  alias:  "guest",             // DEFAULT value  (overridable)
  issuer: #"GCP-System"        // LITERAL TYPE   (frozen; assignment rejected)
};
let k = ApiKey{ key = "abc" };
k.alias;       // "guest"
k.issuer;      // "GCP-System"  (cannot be reassigned)
```

## 10. Nullable & the bottom

```outline
let name: String? = null;      // String | Nothing
let n    = match name {
  null -> "anonymous",
  s    -> s
};
```

`null` has type `Nothing` and unifies with every nullable. `Nothing` is also the bottom
type of diverging branches.

## 11. Pattern matching cookbook

```outline
match x {
  0                        -> "zero",
  n if n < 0               -> "negative",
  { name, age }            -> name + "/" + to_str(age),   // record destructure
  (a, b)                   -> to_str(a) + "," + to_str(b), // tuple destructure
  Circle{ r }              -> r * r,                       // variant w/ fields
  Circle{ r } if r > 100   -> "big",                       // variant + guard
  _                        -> "other"
}
```

Rules: arms unify to one type, unbound fields are wildcards, `_` at the end makes the
match total. Non-exhaustive matches on finite sum types are warned.

## 12. Modules

```outline
module org.example.demo;

import Person, greet from org.example.people;
import * from org.example.utils;

export Person, greet;
```

Mutual imports are legal. Missing symbols give `MODULE_NOT_DEFINED` with the FQN.

## 13. Async / await

```outline
let p1 = async (3 + 4);              // Promise<Int>
let r1 = await p1;                   // 7
let r2 = await (async "hi");         // "hi"

let doubled = async_request("/api")
  .then(r -> r.body.len())
  .catch(e -> 0);
```

## 14. Host plugins

```outline
let repo  = __ontology_repo__<Employee>;          // typed binding to host data
let db    = __sqlite__<Person>("jdbc:sqlite:…");  // constructor-style plugin
```

`__name__<T>(args?)` resolves via the host interpreter's registered constructors.

## 15. Built-ins (always in scope)

| Function   | Type               |
|------------|--------------------|
| `to_str`   | `Any -> String`    |
| `to_int`   | `String -> Int`    |
| `to_float` | `String -> Float`  |

Per-type methods (pick the receiver, then `outline_completion` to enumerate):

- **Int/Double/Number:** `abs`, `ceil`, `pow`
- **String:** `len`, `split`, `contains`, `sub_str`
- **Array:** `map`, `filter`, `reduce`, `some`, `every`, `take_while`, `drop_while`, `len`

---

## 16. Signature syntax sugar — when to use each form

| You want                               | Write this                              |
|----------------------------------------|------------------------------------------|
| one-liner, inferred                    | `let add = (a, b) -> a + b;`            |
| one-liner, type-locked                 | `let add = (a: Int, b: Int): Int -> a + b;` |
| multi-statement body                   | `let f = x -> { let y = …; y + 1 };`    |
| recursion                              | `fx fact(n: Int): Int { if (n<=1) 1 else n*fact(n-1) };` |
| generic                                | `let id = <a>(x: a) -> x;` or `fx id<a>(x: a): a { x };` |
| annotation-as-constraint, nothing more | `let n: Int? = read();`                 |

## 17. Keywords (cheat list)

```
let  var  fx  outline  module  import  export  from
if  else  match  with  is  as  return  this
async  await  sync  true  false  null  unit
```

## 18. Error hygiene

- `outline_parse` → syntax
- `outline_infer` → types (the most useful filter)
- `outline_interpret` → runtime

Run them in that order. Most real bugs surface at **infer** with a precise
location and a short snippet, before any code runs.
