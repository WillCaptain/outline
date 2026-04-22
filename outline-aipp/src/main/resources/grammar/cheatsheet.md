# Outline Cheat Sheet

Everything you need to write a working Outline snippet. Each bullet points to
a deeper section; start here, drill down only when needed.

## Bindings

```outline
let x = 1;                // immutable, inferred Int
let y: String = "hi";     // with type annotation
var z = 0;                // mutable (re-assignable)
```

## Records (JS-style or Outline-style literal)

```outline
let p = {name: "will", age: 10};        // JS-style (also accepts `=`)
let q = {name = "will", age = 10};      // Outline-style, identical semantics
p.name                                  // → "will"
```

See `values-and-types` for list literals and primitive types.

## Lambdas and functions

```outline
let add = (a, b) -> a + b;              // arrow lambda
let sq  = x -> x * x;                   // single param needs no parens
fx greet(name: String) -> String {      // named function form
  "hello " + name
};
```

See `lambdas-and-functions`.

## Type declarations

```outline
outline Person = { name: String, age: Int };
outline Result = Ok | Err;                       // sum type
outline Box = <a>{ value: a };                   // generic
outline Bag = VirtualSet<Person>{};              // specialised VirtualSet
```

See `outline-decl`. VirtualSet gets its own section: `virtualset-and-this`.

## Control flow

```outline
if (age >= 18) "adult" else "minor";

match value with
  | 0  -> "zero"
  | _  -> "other";

obj is Person                           // runtime type check (returns Bool)
obj as Person                           // downcast (type-system level)
```

See `control-flow`.

## Modules

```outline
module org.example.demo
import Person from org.example.model;
export greet;
```

See `modules`.

## Common chain pattern

```outline
let people: Persons = __ontology_repo__<Persons>;
people.filter(p -> p.age >= 18).count()   // VirtualSet; see virtualset-and-this
```
