---
name: outline_code
description: Author, type-check and execute standalone Outline language snippets with a tight diagnostic loop. Use when the user asks to "write / fix / debug / run Outline code" at the language level (e.g. "帮我写一段 outline 计算斐波那契", "这段 outline 代码能编译吗", "outline lambda / match / fx 怎么写", "check if this outline compiles"). Covers the whole draft → parse → infer → interpret loop, uses `outline_completion` to discover members at every `.` anchor, and pulls from `outline_grammar` sections on demand. This skill knows Outline as a pure language only — it has NO knowledge of any ontology, world, VirtualSet collection, or `__ontology_repo__` binding. If the user's request depends on concrete ontology types (decision triggers, actions, virtualset queries over live entities), stop and say so — a domain skill in the owning app handles that.
allowed-tools:
  - outline_parse
  - outline_infer
  - outline_interpret
  - outline_completion
  - outline_grammar
---

# Outline Code Authoring (language-only)

Write correct Outline snippets on the first reply, not the fifth.
You have four atomic tools and a grammar reference. Use them in the order
below; do **not** hand code to the user that has not at least survived
`outline_infer`.

## Scope boundary — read this first

This skill is about **Outline the language** — literals, records, lambdas,
`outline` type declarations, `match`, `if`, modules. Nothing else.

It does **not** know:

- what entities exist in any running world / ontology,
- how `__ontology_repo__<X>` resolves,
- what methods a `VirtualSet<X>` exposes in a specific app,
- how a decision activator / action body / virtualset query is wired.

If the user's ask clearly needs that (e.g. "过滤年龄>30 的员工"), you have
two legitimate moves:

1. Ask for the concrete ontology schema they want you to target, then
   still write a **pure, self-contained** snippet (e.g. declare the
   record type inline as a toy `Employee`, not `__ontology_repo__`).
2. Say this skill can only validate standalone language constructs and
   suggest they invoke the domain skill inside the relevant app
   (decision trigger / action / virtualset query lives in `world` app).

Do **not** fabricate ontology bindings like `__ontology_repo__<Employee>`
to "make the example runnable" — they will fail `outline_infer` with
"variable is not defined" and mislead the user into thinking the
language is broken.

## Philosophy (keep in mind while drafting)

1. **Expression-oriented.** Every construct returns a value. If a branch
   "does nothing" the right answer is `Unit`, not an empty block.
2. **Types are inferred.** Annotations are constraints, not documentation.
   Add them only where inference needs a hint (recursive fns, ambiguous
   literals).
3. **Row-polymorphic records.** Extra fields are fine — consumers keep only
   what they declare. Compose inline.

Need the full write-up? `outline_grammar(section="philosophy")`.

## Workflow — the canonical loop

For every Outline draft, run this loop until `outline_interpret` succeeds
or you decide interpretation is not needed (e.g. the user only asked for a
signature):

### Step 1 — Draft

Write the snippet based on the user's intent. Keep it minimal; prefer one
`let` chain over clever macros. If you are unsure of the surface syntax,
fetch `outline_grammar(section="cheatsheet")` first.

### Step 2 — Parse

```
outline_parse(code = <draft>)
```

- `ok: true` → go to Step 3.
- `ok: false` → read `errors[]`; each is `"line L:C message"`. Fix and
  re-parse. Do **not** try to infer unparseable code.

### Step 3 — Infer (type-check)

```
outline_infer(code = <draft>)
```

- `ok: true` → go to Step 4.
- `infer_errors` non-empty → read the first one; its message already
  carries the source snippet and `@line L:C`. Typical fixes:
  - "variable is not defined" → you referenced something that does not
    exist in the snippet. **If it is an ontology symbol (e.g.
    `__ontology_repo__<X>`, `employees`, `countries`) stop — this skill
    cannot resolve those.** Otherwise fix the typo or bring the binding
    into scope with `let`.
  - "type mismatch" → you annotated one side wrong, or a lambda
    predicate returned the wrong type.
  - If the error is on a `.` chain, go to Step 3a.

### Step 3a — Member discovery (when you hit a `.`)

Whenever you are about to type `receiver.<something>` and you are not
**certain** which members exist, call:

```
outline_completion(code = <code up to the cursor>, offset = -1)
```

Use the returned `items[]` (`label`, `type`, `kind`) to pick a real member.
This applies at every `.` — including inside lambdas like
`xs.map(a -> a.`, where `a` is a lambda parameter and cannot be looked up
from a top-level symbol table.

### Step 4 — Interpret (only when a value is requested)

```
outline_interpret(code = <draft>)
```

- `ok: true` → report `result.display` to the user.
- Upstream errors → the tool refuses to run; loop back to Step 2/3.
- `runtime_error` → treat it as a real bug in the snippet (division by
  zero, index out of range, etc.). Report the message and propose a fix.

If the user only asked for code (not its value), Step 4 is optional — but
it is the cheapest way to prove the snippet works, so default to running it
when the code is self-contained.

## When to reach for `outline_grammar`

`outline_grammar` is a paged reference, not a dump. Fetch only the section
you need:

| Your question                                                   | Section                 |
|-----------------------------------------------------------------|-------------------------|
| "What literals / primitive types / records / lists look like"   | `values-and-types`      |
| "How do I write a lambda / `fx` / annotate parameters"          | `lambdas-and-functions` |
| "How do I declare a record / sum type / generic outline"        | `outline-decl`          |
| "How does `if` / `match` / `is`/`as` work"                      | `control-flow`          |
| "How do `module` / `import` / `export` work"                    | `modules`               |
| "Why does the language work this way" (when user asks)          | `philosophy`            |
| "I need a single-page syntax reminder"                          | `cheatsheet`            |

Do not fetch more than 2 sections per turn; prefer targeted over exhaustive.

## Reporting to the user

When you return a working snippet, keep the reply short:

1. The snippet, in a fenced ```outline block.
2. One sentence of what it does (not how).
3. If you ran `outline_interpret`, the `result.display`.

Do **not** paste tool-call transcripts, error loops, or parse trees. The
user cares about the final code and that it works.

## Non-goals

- Does not read any ontology / world state.
- Does not resolve `__ontology_repo__<...>` symbols.
- Does not know about `VirtualSet` own methods beyond the generic
  language-level notion; those live in the app that owns the collection.
- Does not modify live world state.
- Does not guess type annotations. If inference cannot resolve a symbol,
  stop and ask the user rather than inventing a plausible type.
