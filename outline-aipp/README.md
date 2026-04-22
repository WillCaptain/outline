# outline-aipp

An AIPP host-app that wraps the **Outline** language core (parser + GCP
inference + interpreter) as a bundle of atomic tools and a single
`outline_code` skill, consumable by any AIPP host (e.g. World One).

## Purpose

Outline is a strongly-typed functional DSL. Letting LLMs write Outline
code reliably needs three things:

1. **Static feedback** — before executing, the LLM should be able to
   parse, type-check, and inspect the members of a value.
2. **Live feedback** — while composing `a.foo.bar(...)`, after every `.`
   the LLM should be able to ask "what members are available here?".
3. **Execution** — the LLM needs a way to actually *run* the code it
   wrote and observe the result.

This app exposes exactly those capabilities as AIPP tools, plus one
playbook-level skill that wires them into a reliable write-loop.

## Capabilities (target end-state)

| Tool                 | Semantics                                                                   | Side-effects |
|----------------------|-----------------------------------------------------------------------------|--------------|
| `outline_parse`      | Syntax check; returns AST or syntax error with line numbers.                | none         |
| `outline_completion` | Members / identifiers available at a cursor position. Critical for `a.`.    | none         |
| `outline_infer`      | Type inference over a whole snippet; returns inferred types + error report. | none         |
| `outline_interpret`  | Execute the snippet; returns runtime result or runtime error.               | yes          |
| `outline_grammar`    | Lazy grammar reference, sliced by section (types, patterns, virtualset …).  | none         |

| Skill          | Scope       | Description                                                           |
|----------------|-------------|-----------------------------------------------------------------------|
| `outline_code` | `universal` | Loop `completion → edit → completion → … → infer → fix → interpret`. |

## Milestones

- **M0** (current) — Spring Boot scaffold; empty `/api/tools`, `/api/skills`,
  `/api/widgets`; deploy scripts; registers cleanly into World One.
- **M1** — `outline_parse`.
- **M2** — `outline_infer` + `outline_interpret`.
- **M3** — `outline_completion` (the `a.` killer feature).
- **M4** — `outline_grammar(section?)` + grammar Markdown under
  `resources/skills/outline_code/resources/*.md`.
- **M5** — ship the `outline_code` skill (playbook); verify Router
  auto-activates it on Outline-flavored requests.

## Run

```bash
mvn -q -DskipTests package
cp target/outline-aipp.jar deploy/
./deploy/start.sh -d         # daemon on :8094
./deploy/stop.sh
```

Register into World One (one-shot, idempotent):

```bash
curl -sX POST http://localhost:8090/api/registry/install \
     -H 'Content-Type: application/json' \
     -d '{"app_id":"outline","base_url":"http://localhost:8094"}'
```
