# Values and Types

## Primitive types

| Type      | Example literal        | Notes                                   |
|-----------|------------------------|-----------------------------------------|
| `Int`     | `1`, `42`, `0`         | underscore separators allowed: `1_000`  |
| `Long`    | `100L`, `0l`           | `L`/`l` suffix                          |
| `Float`   | `1.5f`                 |                                         |
| `Double`  | `3.14`                 | default for decimals                    |
| `Number`  | —                      | abstract super of numeric types         |
| `String`  | `"will"`               | double-quoted                           |
| `Bool`    | `true`, `false`        | keywords                                |
| `Unit`    | — (produced by `;`)    | "no value" — result of a pure statement |
| `Nothing` | `null`                 | bottom of nullable sum types            |

## Record literals

Two interchangeable syntaxes:

```outline
let p = {name: "will", age: 10};     // JS-style
let p = {name = "will", age = 10};   // Outline-style
```

You may even mix: `{name: "will", age = 10}`. The field order in the literal
defines the row-polymorphic field order inferred for the type.

Trailing commas are **rejected**.

## List literals

```outline
let xs = [1, 2, 3];             // inferred: List<Int>
let ys = [{name: "a"}, {name: "b"}];
```

The element type is unified across all entries; `[1, "two"]` is a type error.

## Nullable types

A type can be marked nullable with `?`, which desugars to a sum with
`Nothing`:

```outline
let name: String? = null;   // equivalent to String | Nothing
```

`null` has type `Nothing` and unifies with any nullable type.

## Primitive methods

Every primitive carries a small set of built-in methods — `to_str`, arithmetic
/ comparison operators, etc. Use `outline_completion` on a receiver (`"hi".`)
to enumerate them; the set depends on the inferred type.
