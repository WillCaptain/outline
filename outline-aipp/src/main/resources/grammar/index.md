# Outline Language Reference — Index

Outline is a strongly-typed, expression-oriented functional DSL inferred by the GCP
(Generalized Constraint Propagation) type system. Every section below is fetched
separately — call `outline_grammar(section="<name>")`.

| Section                 | When to read it                                                         |
|-------------------------|-------------------------------------------------------------------------|
| `cheatsheet`            | Drafting code from scratch — all core syntax on one page.               |
| `values-and-types`      | Literals, primitives, records (`{k: v}`), lists.                        |
| `lambdas-and-functions` | `a -> body`, `fx name(...)`, parameter / return type annotations.       |
| `outline-decl`          | `outline Name = ...` — records, sum types, generic type decls.          |
| `control-flow`          | `if / else`, `match / with`, `is` / `as`.                               |
| `modules`               | `module`, `import`, `export`, `from`.                                   |
| `philosophy`            | Design principles — pure, type-driven, row-polymorphic.                 |

## Scope

This reference covers **Outline as a language**: syntax, inference, the
core type system. Collection abstractions that depend on a host (most
notably `VirtualSet<T>`, the `~this` covariant self-type used with it, and
the `__ontology_repo__<X>` binding) are **documented by the app that owns
the ontology**, not here. If you are writing code against a live world /
ontology, ask the corresponding app-level skill for its own grammar page.

## Rules of thumb

- **Parse first, infer second, interpret third.** Use `outline_parse` → `outline_infer`
  → `outline_interpret` in that order. Each stage filters a different error class.
- **When you hit a `.`, call `outline_completion`.** Don't guess member names.
- **When you hit an unfamiliar keyword, come back here.** The lexer recognises
  `let`, `var`, `fx`, `outline`, `module`, `import`, `export`, `from`, `is`,
  `as`, `if`, `else`, `match`, `with`, `async`, `await`, `sync`, `return`,
  `this`, `true`, `false`, `null`, `baseNode`, `macro`.
