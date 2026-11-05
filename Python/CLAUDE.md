# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

This is **not a software project** — there is no application, package, build system, or test suite. It is a personal, self-contained Python study curriculum: 15 numbered `.py` files that double as reference material, written for a JS developer learning Python from scratch through to advanced/professional usage. `INDICE.md` is the authoritative table of contents and reading order; read it first to see what each file covers before editing anything.

## Commands

There is no build, lint, or test framework. The only relevant commands:

```bash
# Check a single file's syntax is valid (closest thing to a "test" here)
python3 -m py_compile 07_colecciones.py

# Check every file at once
for f in [0-9]*.py; do python3 -m py_compile "$f" && echo "OK: $f" || echo "FALLO: $f"; done
rm -rf __pycache__

# Run one file directly to see it execute for real
python3 12_programacion_orientada_a_objetos.py
```

Running a file directly will often **stop partway through with an unhandled exception — this is intentional**, not a bug. Several files contain bare statements written specifically to demonstrate what an error looks like (e.g. `word[0] = "P"` to show a `TypeError`, or `coordinate[0] = 100` for a tuple). These are meant to be read, not executed past. Every such line, and every `print(...)` call in these files, already carries a trailing `#` comment with the real verified output/exception, so there's normally no need to run anything to know what a section produces.

`data.txt` in this directory is a real artifact produced by running `13_archivos_y_stdlib.py`'s file-I/O examples, not stray output to delete.

## Content conventions (must be preserved when adding or editing material)

These rules were deliberately established through many iterations and apply uniformly across all 15 files. Breaking them inconsistently across files is the main way this repo can degrade:

- **Language split**: explanatory prose is Spanish, written inside triple-quoted `"""..."""` blocks used as pseudo-comment sections (one block = one concept). All code — identifiers, function/class names, sample string data — is in English. Code itself has **no inline `#` comments explaining logic**; explanations live only in the prose blocks above the code.
- **Verified-output comments**: the only comments allowed inside code are trailing `#` comments on `print(...)` calls (and on intentional error-demo statements) showing the *exact* real value or exception — always confirmed by actually running the code, never guessed. This is what lets someone read a file without executing it.
  - Non-deterministic output (`random`, `datetime.now()`, `set` iteration order, network calls, thread timing) is explicitly labeled as varying (e.g. `# e.g. 4 (random, changes every run)`) instead of showing a fixed, misleading value.
  - When a loop/comprehension prints multiple times, don't put a comment on the `print()` line itself — add one small `#` comment block immediately after the loop listing each real output line in order.
- **JS-comparison tags**: each docstring section that introduces a distinct Python concept opens with one of exactly three labels, on its own line (right below a `====` banner if the section has one, otherwise as the first line of the block): `[IGUAL QUE JS]`, `[SIMILAR A JS, CON DIFERENCIAS]`, or `[EXCLUSIVO DE PYTHON — no existe en JS]`. Skip the tag only on pure elaboration/follow-up blocks that continue an already-tagged concept, or on meta/best-practices summary sections that don't describe one specific language construct.
- **Scope boundaries** (deliberate, not gaps to "complete"): no CPython/engine internals (memory model, descriptors, `__new__`, metaclasses); no GUI frameworks (PyQt5 explicitly excluded); no testing frameworks, packaging, or virtual-env tooling; no third-party libraries beyond a few stdlib-adjacent essentials (`requests`, already covered). Big-O-style reasoning for *why* to choose one data structure over another is in scope; CPython implementation details are not.
- **File granularity**: one file per large topic (not per micro-feature) — e.g. all of lists/tuples/sets/dicts live in `07_colecciones.py`, not split into four files. When adding new content, extend the most relevant existing file rather than creating a new one, unless it's a genuinely separate large topic (as `15_concurrencia_asyncio.py` was).
- Whenever a file is added, renamed, or its topic list changes meaningfully, **update `INDICE.md`** to match.
