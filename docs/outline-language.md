# Outline Language Guide

Outline is the expression language that targets GCP's type engine. Every Outline program is a sequence of statements; the value of the program is the last evaluated expression.

> **New here?** Start with the [Quick Start](./quickstart.md) to run your first parser flow.  
> **Need Java integration details?** See [SDK Integration](./sdk-integration.md).

---

## Quick Reference

| Construct | Syntax | Example |
|---|---|---|
| Variable | `let name = expr` | `let x = 42` |
| Mutable variable | `var name = expr` | `var n = 0` |
| Assignment | `name = expr` | `n = n + 1` |
| Function | `(p1, p2) -> body` | `(x, y) -> x + y` |
| Curried function | `p1 -> p2 -> body` | `x -> y -> x + y` |
| Function call | `f(arg)` | `add(3, 4)` |
| Record (entity) | `{field = value, ...}` | `{name = "Alice", age = 30}` |
| Record extension | `base{field = value}` | `person{last_name = "Zhang"}` |
| Tuple | `(a, b, c)` | `("Alice", 30)` |
| Array | `[a, b, c]` | `[1, 2, 3]` |
| Dict | `[key: value, ...]` | `["name": "Alice", "age": 30]` |
| Block | `{ stmt; stmt; expr }` | `{ var n = 0; n + 1 }` |
| If | `if cond then a else b` | `if x > 0 then x else -x` |
| Match | `match expr { pattern => expr }` | see §8 |
| Outline type | `outline Name = { field: Type }` | see §11 |
| Namespace | `namespace name` | `namespace geo` |
| Import | `import { a, b } from module` | `import { PI } from math` |
| Export | `export name` | `export let PI = 3.14` |
| Plugin call | `__id__<Type>(args)` | `__sqlite__<Employee>("jdbc:...")` |

---

## 1. Variables

### Immutable (`let`)

```outline
let x = 42;
let greeting = "hello " + to_str(x);
let double = x -> x * 2;
```

### Mutable (`var`)

```outline
var counter = 0;
counter = counter + 1;
counter = counter + 1;
counter   // → 2
```

### Type Annotations (optional)

GCP infers types automatically. Annotations are only needed to disambiguate ambiguous expressions or to document intent:

```outline
let add = (a: Int, b: Int) -> a + b;
let greet = (name: String) -> "Hello, " + name;
```

---

## 2. Literals

```outline
// Integer
42
-7
0

// Float
3.14
-0.5
1.0e10

// String
"hello"
"hello " + "world"   // concatenation with +

// Boolean
true
false

// Unit (no meaningful value)
unit
```

---

## 3. Arithmetic and Comparison Operators

```outline
// Arithmetic
1 + 2     // 3
10 - 3    // 7
4 * 5     // 20
10 / 3    // 3 (integer division when both operands are Int)
10 % 3    // 1

// String concatenation
"foo" + "bar"   // "foobar"

// Comparison (all return Bool)
3 > 2     // true
3 >= 3    // true
2 < 5     // true
2 <= 2    // true
1 == 1    // true
1 != 2    // true
```

> **No `&&` / `||`**: Boolean logic uses `if` expressions or function composition instead of inline boolean operators.

---

## 4. Functions

Outline functions are **first-class values** and support currying and closures.

### Single-parameter function

```outline
let double = x -> x * 2;
double(5)    // 10
```

### Multi-parameter function (tuple syntax)

```outline
let add = (x, y) -> x + y;
add(3, 4)    // 7
```

### Curried function

```outline
let add = x -> y -> x + y;
let add5 = add(5);    // partial application → y -> 5 + y
add5(3)               // 8
add(5)(3)             // 8
```

### Block body

```outline
let factorial = n -> {
    if n <= 1 then 1
    else n * factorial(n - 1)
};
factorial(5)   // 120
```

### Closures

Functions capture outer variables lexically:

```outline
let x = 100;
let get_x = () -> x;
get_x()   // 100
```

### Mutable closures

```outline
let make_counter = () -> {
    var n = 0;
    () -> { n = n + 1; n }
};
let c = make_counter();
c()   // 1
c()   // 2
c()   // 3
```

### Anonymous functions

```outline
[1, 2, 3].map(x -> x * 2)     // [2, 4, 6]
[1, 2, 3].filter(x -> x > 1)  // [2, 3]
```

---

## 5. Records (Inline Entities)

A **record** is an anonymous product type — a named bag of fields and methods.

### Creating a record

```outline
let person = {
    name = "Alice",
    age  = 30
};
person.name   // "Alice"
person.age    // 30
```

### Methods and `this`

```outline
let person = {
    name     = "Alice",
    age      = 30,
    greet    = () -> "Hello, I am " + this.name,
    birthday = () -> { this.age = this.age + 1; this }
};
person.greet()   // "Hello, I am Alice"
```

### Record extension (inheritance)

Extend an existing record to create a new one that inherits all fields:

```outline
let base = { x = 10, describe = () -> "x=" + to_str(this.x) };

let child = base{
    y       = 20,
    describe = () -> "x=" + to_str(this.x) + ", y=" + to_str(this.y)
};

child.x           // 10  (inherited)
child.y           // 20  (own field)
child.describe()  // "x=10, y=20"  (overridden)
```

---

## 6. Tuples

A **tuple** is an ordered, immutable collection of values of potentially different types. Fields are accessed by numeric index.

```outline
let point = (10, 20);
point.0   // 10
point.1   // 20

let triple = ("Alice", 30, true);
triple.0   // "Alice"
triple.1   // 30
triple.2   // true
```

### Tuples with methods

```outline
let named_point = (10, 20, () -> "(" + to_str(this.0) + ", " + to_str(this.1) + ")");
named_point.2()   // "(10, 20)"
```

---

## 7. Arrays

**Arrays** are ordered mutable collections of a uniform element type.

### Literals and Access

```outline
let nums = [1, 2, 3, 4, 5];
nums[0]    // 1  (zero-based index)
nums[2]    // 3
```

### Built-in Methods

```outline
let nums = [1, 2, 3, 4, 5];

// Transform
nums.map(x -> x * 2)          // [2, 4, 6, 8, 10]
nums.filter(x -> x > 2)       // [3, 4, 5]
nums.reduce(0, (acc, x) -> acc + x)  // 15

// Test
nums.some(x -> x > 4)         // true
nums.every(x -> x > 0)        // true

// Slice
nums.take_while(x -> x < 4)   // [1, 2, 3]
nums.drop_while(x -> x < 4)   // [4, 5]

// Inspect
nums.len()    // 5
```

### String split (returns `[String]`)

```outline
"a,b,c".split(",")   // ["a", "b", "c"]
```

---

## 8. Dicts

**Dicts** are key-value maps.

```outline
let info = ["name": "Alice", "age": 30];
info["name"]   // "Alice"
info.len()     // 2
```

---

## 9. Block Expressions

A **block** is a sequence of statements enclosed in `{}`. The value of the block is the last expression:

```outline
let result = {
    let x = 10;
    let y = 20;
    x + y
};
result   // 30
```

Blocks introduce a new lexical scope. Variables declared inside a block are not visible outside.

---

## 10. If Expressions

`if` is an **expression** — it always returns a value. Both branches must produce compatible types.

```outline
// Ternary style
if x > 0 then x else -x

// Multi-line
if age >= 65 then
    "retirement eligible"
else
    "still working"

// Nested
if score >= 90 then "A"
else if score >= 80 then "B"
else if score >= 70 then "C"
else "F"
```

---

## 11. Match Expressions

`match` provides exhaustive pattern dispatch.

### Literal matching

```outline
let describe = n -> match n {
    0 => "zero",
    1 => "one",
    _ => "many"
};
describe(0)   // "zero"
describe(5)   // "many"
```

### Guard conditions

```outline
let classify = x -> match x {
    n if n < 0 => "negative",
    0           => "zero",
    n if n > 0  => "positive"
};
```

### Option type (union) matching

```outline
outline Shape = Circle | Square | Triangle;

let area = s -> match s {
    Circle   => 3.14,
    Square   => 1.0,
    Triangle => 0.5
};
```

### Record destructuring

```outline
let describe = p -> match p {
    {name, age} => name + " is " + to_str(age)
};
describe({name = "Alice", age = 30})   // "Alice is 30"
```

### Tuple destructuring

```outline
let swap = pair -> match pair {
    (a, b) => (b, a)
};
swap(("Alice", 42))   // (42, "Alice")
```

---

## 12. Outline Type Declarations

`outline` declares a **named type** — used to give structure to data and enable type-checked field access.

### Entity outline

```outline
outline Employee = {
    id:   Int,
    name: String,
    age:  Int
};
```

### Enum outline (union / option type)

```outline
outline Color = Red | Green | Blue;
```

### Parameterized outline (generic)

```outline
outline Pair<A, B> = { first: A, second: B };
```

### Extending an outline

```outline
outline Manager = Employee {
    team_size: Int,
    budget:    Number
};
```

---

## 13. Namespaces and Modules

### Namespace declaration

```outline
namespace geo;

outline Country = { code: String, name: String };
let default_country = { code = "US", name = "United States" };
```

### Export

```outline
namespace math;

let PI = 3.14159;
export PI;

// Or inline:
export let E = 2.71828;
```

### Import

```outline
import { PI, E } from math;
PI * 2.0   // 6.28318
```

---

## 14. Plugins — Calling Java from Outline

Plugins let Outline programs call Java objects registered via `OutlineInterpreter.registerConstructor()`.

```outline
// Type-parameterized plugin call (no value args)
let repo = __sqlite_repo__<Employee>;

// With value arguments
let db = __sqlite__<Person>("jdbc:sqlite:myapp.db");
let cache = __redis__<String>("localhost", 6379);
```

The `__name__` syntax calls the constructor registered as `"name"` in the interpreter. See [SDK Integration](./sdk-integration.md) for module boundaries and integration entry points.

---

## 15. Built-in Functions

These global functions are always in scope:

| Function | Signature | Description |
|---|---|---|
| `to_str(x)` | `Any -> String` | Convert any value to its string representation |
| `to_int(s)` | `String -> Int` | Parse a string to integer |
| `to_float(s)` | `String -> Float` | Parse a string to float |

### Number built-ins

```outline
let x = 42;
x.abs()          // Int — absolute value
x.ceil()         // Int — ceiling (identity for integers)
x.pow(2.0)       // Double — power
```

### String built-ins

```outline
let s = "hello, world";
s.len()              // Int — character count
s.split(",")         // [String] — split by delimiter
s.contains("world")  // Bool — substring check
s.sub_str(0, 5)      // String — "hello"
```

### Array built-ins

```outline
let a = [3, 1, 4, 1, 5];
a.len()                       // Int
a.map(x -> x * 2)             // [Int]
a.filter(x -> x > 2)          // [Int]
a.reduce(0, (acc, x) -> acc + x)  // Int
a.some(x -> x > 4)            // Bool
a.every(x -> x > 0)           // Bool
a.take_while(x -> x < 5)      // [Int]
a.drop_while(x -> x < 5)      // [Int]
```

---

## 16. Type System Summary

GCP infers types through **constraint propagation** — you rarely need to write explicit annotations.

### Primitive types

| Type | Description |
|---|---|
| `Int` | 32/64-bit integer |
| `Float` / `Double` | Floating-point number |
| `Number` | Supertype of all numeric types |
| `String` | UTF-8 text |
| `Bool` | `true` or `false` |
| `Unit` | No meaningful value (e.g. side-effect functions) |
| `Nothing` | Bottom type (no value, used in diverging expressions) |
| `Any` | Top type — any value is compatible |

### Composite types

| Type | Syntax | Example |
|---|---|---|
| Array | `[T]` | `[Int]` |
| Dict | `[K:V]` | `[String:Int]` |
| Function | `A -> B` | `Int -> String` |
| Tuple | `(A, B)` | `(String, Int)` |
| Union (option) | `A \| B` | `Circle \| Square` |
| Outline entity | named type | `Employee` |

### Subtyping rules

```outline
// Any is the top type — everything is a subtype of Any
let x: Any = 42;
let y: Any = "hello";

// Nothing is the bottom type — an expression of type Nothing can appear anywhere
// (e.g. in unreachable branches of match)

// Number is a supertype of Int, Float, Double
let n: Number = 42;    // Int → Number is valid
let m: Number = 3.14;  // Float → Number is valid
```

---

## Common Mistakes

### Chaining after a non-chainable operation

```outline
// WRONG — filter returns [Int], not an array that has .first()
[1, 2, 3].filter(x -> x > 1).first()    // ERROR: first not on Array

// CORRECT — filter returns the array, then index
[1, 2, 3].filter(x -> x > 1)[0]
```

### Using `&&` / `||`

```outline
// WRONG — boolean operators not supported inline
if x > 0 && y > 0 then "both positive"   // SYNTAX ERROR

// CORRECT — nest if expressions
if x > 0 then (if y > 0 then "both positive" else "only x") else "neither"
```

### Accessing a non-existent field

```outline
let p = { name = "Alice", age = 30 };
p.email   // ERROR: field 'email' not found on this entity
```

Check available fields in Java: `ast.meta().membersOf("p", offset)`.

### Forgetting `this` in methods

```outline
let p = {
    name = "Alice",
    greet = () -> "Hello, " + name   // WRONG: 'name' not in scope here
};

let p = {
    name = "Alice",
    greet = () -> "Hello, " + this.name   // CORRECT
};
```
