# Lambdas and Functions

Outline has two forms: lightweight lambdas (arrow syntax) and named functions
(`fx`). Both are first-class values.

## Arrow lambdas

```outline
let inc = x -> x + 1;                // single param
let add = (a, b) -> a + b;           // multi-param
let const_one = () -> 1;             // zero params

let strict = (x: Int) -> Int => x + 1;   // with type annotations
```

Single-parameter lambdas omit the parentheses. Multi-parameter or typed
parameters must use `(...)`.

## Block bodies

```outline
let f = x -> {
  let sq = x * x;
  sq + 1
};
```

The last expression in the block is the return value. A trailing `;`
discards it — the block then returns `Unit`.

## Named functions (`fx`)

```outline
fx greet(name: String) -> String {
  "hello " + name
};

greet("world")
```

`fx` is syntactic sugar for `let <name> = ...`; it allows recursion
(`fx fact(n) -> ...` can reference `fact` in its body).

## Closures

Lambdas capture their enclosing scope:

```outline
let multiplier = k -> (x -> x * k);
let times3 = multiplier(3);
times3(4)                               // → 12
```

## Type annotations on arguments and returns

```outline
let div: (Int, Int) -> Int = (a, b) -> a / b;
```

When the return type cannot be inferred (e.g. recursive function), annotate
it explicitly after `->`.
