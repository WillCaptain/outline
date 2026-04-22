# `outline` — type declarations

`outline` is how you introduce a named type. It covers record types, sum
types, and generics.

## Record type

```outline
outline Person = { name: String, age: Int };
outline Point  = { x: Float, y: Float };
```

Fields are comma-separated. The `:` is mandatory in declarations (unlike in
literals, where both `:` and `=` work).

## Sum type

```outline
outline Result = Ok | Err;
outline Shape  = Circle | Square | Triangle;
outline Maybe  = Just | Nothing;       // or equivalently: use `T?`
```

### Tagged variants with structure (structural ADTs)

A sum type may give each variant its own record structure:

```outline
outline Status = Pending{name:String} | Approved{boss:String};
```

Outline is a **structural** language, so tags name a *shape*, not a
nominal class. The rules for working with `Status`:

1. **Free-standing literals match by structure, not by name.** Because
   `Pending{name:String}` and `Pending{boss:String}` describe two
   different shapes that happen to share a tag, both of the following
   are legal, and they are two *different* types:

   ```outline
   let a = Pending{ name: "Will" };   // Pending & {name:String}
   let b = Pending{ boss: "Will" };   // Pending & {boss:String}
   ```

   Neither is forced to fit `Status`; a free-standing tagged literal is
   just a record wearing a tag.

2. **Upcasting to the ADT happens at the point of use.** When a value
   reaches a context typed as `Status` (binding annotation, function
   argument, return slot), the checker verifies structural
   belonging:

   ```outline
   let s: Status = Pending{ name: "Will" };   // ok — matches Pending's shape
   let f = x:Status -> "ok";
   let r = f(Pending{ name: "Will" });        // ok
   let e = f(Pending{ boss: "Will" });        // error: does not belong to Status
   ```

3. **Row polymorphism is allowed.** Extra fields beyond what the variant
   requires do not break belonging — only the declared fields must match:

   ```outline
   let s: Status = Pending{ name: "Will", extra: 42 };   // ok
   ```

4. **Qualified constructor `Owner.Variant{...}`** forces the upcast
   eagerly and does the same structural check at the literal:

   ```outline
   let s = Status.Approved{ boss: "Will" };              // ok
   let x = Status.Approved{ name: "Will" };              // error: missing 'boss'
   ```

   Diagnostics are phrased as *belonging* failures
   (`literal does not belong to 'Approved' of Status: missing 'boss'`)
   rather than "field not found", since the structure itself is what
   fails to qualify.

5. **Match uses the same structural rule.** Each arm describes a shape
   and may bind fields with `as`:

   ```outline
   let show = x:Status -> {
     match x {
       Pending{ name as n } -> n,
       Approved{ boss as n } -> n,
       _ -> "others"
     }
   };
   ```

## Generic record

```outline
outline Box = <a> { value: a };
let b: Box<Int> = {value = 1};
```

Type parameters are written in `<...>` between the name and `=`.

## Methods inside a record

```outline
outline Counter = <a> {
  count: Int,
  inc:   Unit -> Counter<a>,
  peek:  Unit -> a
};
```

Function-typed fields are methods. Inside a declaration you may reference
the enclosing outline by name (with its type parameters) as the return
type, as shown above.

> Host-provided collection types (`VirtualSet<T>` and its `~this`
> covariant self-type) are documented by the owning app, not here — see
> the `outline-decl` section of the app-level grammar reference in that
> app's grammar tool.

## Nested / refined outlines

An outline declaration can reference another outline already in scope:

```outline
outline Address = { street: String, city: String };
outline Person  = { name: String, home: Address };
```

Order within a module is free — `Person` can appear before `Address`; the
type-checker resolves references lazily.
