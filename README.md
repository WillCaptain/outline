# Outline

**Outline is a type-inferred, expression-oriented functional language.**
You never write type annotations. The compiler (GCP — *Generalized Constraint Propagation*)
figures them out by reading how your values are *used*, not how they are *declared*.

```outline
let full_name = p -> p.given + " " + p.surname;
//                  ^^^^^^^  ^^^^^^^^^
// GCP reads the two field accesses and synthesises:
//   p : { given: String, surname: String }
//
// Neither Haskell nor TypeScript will give you that without an annotation.
full_name({ given = "Alice", surname = "Smith", age = 30 });   // extras ok (row polymorphism)
```

> No `interface`. No `data Person = …`. The shape of `p` is the *outline* the compiler draws
> from the code itself. That is where the language gets its name.

---

## Why another language?

| Scenario | What Outline does differently |
|---|---|
| "I'd like to try something before naming it." | No nominal types. Write the code; the shape is the type. |
| "My types drift when I refactor." | Types track *usage*. Change how a value is used — the inferred type changes with you, not after you. |
| "Annotations clutter my DSL." | Zero annotations compile. Annotations, when you do write them, behave as **hard constraints** (violations become inference errors at the *use site*). |
| "I want ADTs *and* row polymorphism." | Sum types like `Pending{name:String} \| Approved{boss:String}` carry structure **and** tolerate extra fields per variant. |
| "My fluent API loses its type after one call." | `~this` / future-`this` keeps method chains typed as the concrete receiver, not the base. |
| "I want an embedded scripting language with a real type system." | Outline is hostable: parse → infer → interpret, each stage exposed as a separate SDK call. |

---

## A 60-second tour

```outline
// 1. Bindings — let is immutable, var is mutable
let x = 42;                          // Int
var n = 0; n = n + 1;

// 2. Functions are expressions, curried by default
let add = x -> y -> x + y;
let add5 = add(5);                   // partial application

// 3. Records are structural; methods see themselves via `this`
let person = {
  name = "Alice",
  age  = 30,
  greet = () -> "Hi, I'm " + this.name
};

// 4. `if` and `match` are expressions that RETURN a value
let label = if (person.age >= 18) "adult" else "minor";
let kind  = match person {
  { name = "root" }        -> "admin",
  { age = a } if a >= 18   -> "adult",
  _                        -> "other"
};

// 5. Named types only when you want to name them
outline Shape = Circle{r:Int} | Rect{w:Int, h:Int} | Dot;

let area = s -> match s {
  Circle{ r }        -> r * r,
  Rect{ w, h }       -> w * h,
  Dot                -> 0
};

// 6. Generics + self-type chaining
outline Stream = <a> {
  data:   [a],
  filter: (p: a -> Bool) -> this{ data = data.filter(p) },
  map:    <b>(f: a -> b) -> this{ data = data.map(f) }
};

// Extend Stream with LLM operators — NO changes to Stream itself.
// `this{...}` in Stream.map / Stream.filter returns THIS receiver, so every
// inherited call on an LLMStream still produces an LLMStream — with `.prompt`
// and `.complete` reachable through the whole chain.
outline LLMStream = <a> Stream<a> {
  model:    String,
  prompt:   (p: a -> String) -> this{ data = data.map(p) },       // a → String
  complete: (f: String -> String) -> this{ data = data.map(f) }   // ask the model
};

let chat = LLMStream{ data = ["cats", "rust"], model = "gpt-5" };

chat
  .filter(topic -> topic.len() > 2)        // LLMStream<String>  (inherited)
  .prompt(topic -> "Write a haiku about " + topic)
  .complete(req -> __llm__(this.model, req))   // still LLMStream<String>
  .map(reply -> reply.sub_str(0, 140))      // still LLMStream<String>
  .data;                                    //  [String]  — two haikus
```

---

## Six things Outline does that most ML/JS-adjacent languages don't

### 1. Structural type synthesis — no annotations, no shapes to name

```outline
let greet = p -> "Hello, " + p.given + " " + p.surname + "!";
// p : { given: String, surname: String }   ← synthesised purely from usage
```

Call it with `{ given, surname, age }` or `{ given, surname, title }` — the extras are fine.
Row polymorphism is the *default*, not an opt-in.

### 2. Backward constraint propagation

Types flow **up** from literals *and* **down** from how a value is later used.

```outline
let lift = sel -> pred -> entity -> pred(sel(entity));

let get_score  = player -> player.score;
let is_passing = s      -> s >= 60;

let check = lift(get_score)(is_passing);
// GCP reasons backward through the chain:
//   is_passing needs score >= 60  ⇒  score : Int
//   get_score  reads player.score ⇒  player : { score: Int }
// Final:
//   check : { score: Int } -> Bool
```

No annotations. No `where`. No holes.

### 3. Structural ADTs with row polymorphism

```outline
outline Status = Pending{ name: String } | Approved{ boss: String };

let a = Pending{ name = "Will" };            // free-standing literal, tag + shape
let b = Status.Approved{ boss = "Will" };    // qualified upcast to Status

let f = x: Status -> match x {
  Pending{ name as n }  -> n,
  Approved{ boss as n } -> n
};

f(Pending{ name = "Will", extra = 42 });     // extras allowed — the variant's
                                             // REQUIRED fields are what counts
```

A variant's tag names a *shape*, not a class. Errors read as
*"literal does not belong to 'Approved' of Status: missing 'boss'"* —
the structure is what fails to qualify, not a missing field.

### 4. Future-`this` — method chains keep the concrete type

```outline
let animal = {
  walk = () -> this,
  age  = 40
};

let me = animal {
  talk = () -> this,
  name = "Will",
  age  = 30
};

me.walk().talk().name;     // "Will"  — walk() returns `me`, not `animal`
```

`this` is the *receiver*, not the *defining entity*. Extensions inherit methods and the
chain stays typed as the sub-entity. No F-bound "self-type" dance.

`this{...}` is the copy-constructor variant: it produces a **new instance whose type is
the concrete receiver's type**, with selected fields replaced. That is exactly what lets
`LLMStream` above inherit `Stream.map` / `Stream.filter` and still return an `LLMStream`
with `.prompt` / `.complete` reachable downstream — without touching `Stream` at all.

### 5. Literal types as constant fields

```outline
outline ApiKey = {
  key:    String,
  access: String,
  alias:  "guest",            // DEFAULT value — overridable
  issuer: #"GCP-System"       // LITERAL TYPE — frozen at construction
};

let k = ApiKey{ key = "abc", access = "admin" };   // alias & issuer auto-fill
// k.issuer = "other";                              // ← inference error
```

The type `#"GCP-System"` is inhabited by exactly one value. Assignment to anything
else is rejected structurally.

### 6. Full rank-1 inference — Church numerals fall out for free

```outline
let zero = f -> x -> x;
let succ = n -> f -> x -> f(n(f)(x));

let two   = succ(succ(zero));
let three = succ(two);

let church_add = m -> n -> f -> x -> m(f)(n(f)(x));
let decode     = n -> n(x -> x + 1)(0);

decode(church_add(two)(three));   // → 5
```

Zero annotations. Zero `Proxy` tricks. Higher-order inference that actually works.
(We document the rank-2 wall — `church_mul` — in the playground chapter
["Full Dynamic Inference"](https://…/playground).)

---

## Getting started

```bash
git clone https://github.com/WillCaptain/outline.git
cd outline/outline
mvn install -DskipTests
```

```java
// Minimal embedding
var ast = new OutlineParser().parse("""
    let p = { name = "Alice", age = 30 };
    p.name
    """);
ast.asf().infer();                                     // type inference
System.out.println(ast.errors());                      // []
System.out.println(new OutlineInterpreter().run(ast)); // "Alice"
```

### Try it live: https://12th.ai/playground/outline

The reference playground (12th) ships 35+ interactive lessons including a
**"⭐ Unique Features"** chapter that demoes every bullet on this page:

- Structural Type Synthesis
- Backward Constraint Propagation
- Future `this`: Self-Type Extension Chaining
- Generic Entities with Type Parameters
- Literal Types as Constant Fields
- Full Dynamic Inference: Church Numerals

---

## Learn more

| I want to… | Go to |
|---|---|
| ...write my first Outline script | [`docs/quickstart.md`](docs/quickstart.md) |
| ...see every syntactic form on one page | [`docs/cheatsheet.md`](docs/cheatsheet.md) |
| ...read the full language guide | [`docs/outline-language.md`](docs/outline-language.md) |
| ...embed Outline in a Java host | [`docs/sdk-integration.md`](docs/sdk-integration.md) |
| ...understand the compiler pipeline | [`spec/architecture.md`](spec/architecture.md) |
| ...read the philosophy (3 core ideas) | [`outline-aipp/src/main/resources/grammar/philosophy.md`](outline-aipp/src/main/resources/grammar/philosophy.md) |

---

## Status

Outline is driven by the [GCP](https://github.com/WillCaptain/gcp) type engine.
Both are production-use components of the `entitir` / `world-one` ontology stack, but the
language itself is meant to be general-purpose and embeddable. Issues and PRs welcome.

## License

See [LICENSE](LICENSE).
