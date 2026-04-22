# Control Flow

Outline is expression-oriented: every control-flow construct **is an
expression** that produces a value. There are no statements in the C sense.

## `if / else`

```outline
let label = if (age >= 18) "adult" else "minor";

let x = if (n > 0) {
  let sq = n * n;
  sq + 1
} else 0;
```

Both branches must unify to a common type. If you omit `else`, the result is
implicitly `T | Unit` (useful only when you don't care about the value).

## `match / with`

```outline
let describe = n -> match n with
  | 0     -> "zero"
  | 1     -> "one"
  | _     -> "many";
```

Rules:

- Each arm is `| pattern -> expression`.
- `_` is the wildcard; use it as the last arm to make the match total.
- All arms must unify to the same result type.
- The inferencer warns on non-exhaustive matches over finite sum types.

### Matching records

```outline
match p with
  | {name = "root"}        -> "admin"
  | {age = a} if a >= 18   -> "adult"
  | _                      -> "other";
```

Bind fields by name; unbound fields are implicit wildcards. Row-polymorphism
means extra fields are tolerated — `{name = "root"}` matches any record
with a `name` field of value `"root"`.

### Matching sum types

```outline
outline Result = Ok | Err;

match r with
  | Ok   -> "success"
  | Err  -> "failure";
```

## `is` / `as`

Runtime type inspection and type-system downcast, respectively.

```outline
if (v is Employee) {
  let e = v as Employee;     // narrows `v` to Employee in this scope
  e.name
} else "unknown"
```

- `v is T` — Boolean at runtime.
- `v as T` — type-system cast; fails at inference if the cast is unsound.
  Prefer `is ... as ...` pairs over bare `as`.

## Early return: `return`

Inside an `fx` body, `return expr;` short-circuits. Rarely needed in
idiomatic Outline — most functions are single expressions.

```outline
fx find(xs: Employees, key: String) -> Employee? {
  match xs.filter(e -> e.name == key).count() with
    | 0 -> null
    | _ -> xs.filter(e -> e.name == key).first()
};
```
