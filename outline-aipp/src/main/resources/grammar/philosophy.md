# Outline Philosophy

Three ideas that explain the language's surface. Keep them in mind when
writing Outline — they tell you which kind of solution is idiomatic.

## 1. Expression-oriented

There are no statements in the traditional sense. `if`, `match`, blocks, and
function bodies all produce values. When something "looks like a statement"
(e.g. a bare `;`), what you get back is `Unit` — the explicit absence of a
value. If your code is stringing side effects together, you are probably
using the wrong language; Outline is for describing *what things are*.

## 2. Types are inferred, not declared

GCP inference resolves types bottom-up from literals and top-down from
usage. Annotations are opt-in, not required — and when you do add them,
they act as *constraints*, not mere documentation: an incompatible use
downstream becomes an inference error at the use site.

This makes type errors the preferred debugging channel. Before running
`outline_interpret`, run `outline_infer` — most real mistakes surface there
with precise locations and a snippet.

## 3. Row-polymorphic records

Record types track structural subtyping. Any record with fields
`{name, age}` unifies with a consumer that wants `{name}` — the extra
fields are simply ignored. You rarely need to name intermediate record
types; compose them inline and let inference do the rest.

---

Two more principles — the **covariant self-type `~this`** (which keeps
chained collection operators fully typed) and **ontology as the source of
truth** (where initial collection values come from) — only apply when
Outline is hosted inside a world / ontology runtime. They are documented
by the hosting app's own grammar reference, not here.
