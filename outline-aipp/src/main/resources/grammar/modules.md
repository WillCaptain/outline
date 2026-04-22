# Modules

An Outline file is a module. The module header and import/export clauses
are what wire files into a program.

## Module header

```outline
module org.example.onboarding
```

The header is **optional** for snippets executed via `outline_interpret`
but **required** when a file is imported from elsewhere. Use reverse-DNS
style names; GCP resolves imports by this fully-qualified name.

## `import`

```outline
import Employee, Employees from org.example.model;
import * from org.example.utils;
```

- `import A, B from M;` — pull named symbols.
- `import * from M;` — pull all exported symbols.
- Aliasing is not yet supported — rename at the call site instead.

## `export`

```outline
export Person, greet;
```

Only exported names are visible to importers. Un-exported declarations are
module-private.

Multiple `export` statements are allowed and are unioned. Exporting the
same symbol twice is a no-op (not an error).

## `from`

Used only as part of `import ... from ...`; not a standalone form.

## Resolution rules

- Imports are resolved at the `ASF` (AST forest) level — all modules in the
  same compilation unit see each other.
- Mutual imports are legal. GCP pre-registers every module shell before the
  first inference pass, so `A` can import from `B` and vice versa.
- Unresolved symbols produce a `MODULE_NOT_DEFINED` error with the
  fully-qualified name — check the `module` header spelling first.
