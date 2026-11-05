# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A personal Java study repo: standalone, heavily commented teaching files (comments/explanations are in Spanish, often comparing Java to JavaScript). No build tool, no tests, no dependencies, not a git repo. JDK 26 is installed.

`README.md` (root) is the user-facing entry point: what to install per section and how to run each part — keep it in sync when requirements change. Other non-Java files are unrelated notes: `zillow.md` (interview prep), `ultimoDia.js` (JS exercises).

## Running

Run every file from the repo root with the source-file launcher — no `javac` step:

```sh
java v1_fundamentals/part1_first_steps/HowJavaWorks.java
java v2_oop/part2_pillars/Inheritance.java
java v2_oop/part1_basics/AccessModifiers.java   # multi-file: launcher compiles its modifiers/* subpackages on demand
```

Everything outside the Maven projects `v9_tools/` and `v15_spring/` compiles together (`javac -d <tmp> $(find . \( -path ./v15_spring -o -path ./v9_tools \) -prune -o -name '*.java' -print)`) because rewritten guides keep their example types as static nested classes. Keep it that way: never add top-level helper classes that could collide with another file in the same package.

### `v9_tools/` (tools: Maven, testing, quality) is a Maven project too

A single-module Maven project WITHOUT the Spring parent (everything explicit in its `pom.xml`). Guides in `src/main/java/com/practice/tools/<part>/<topic>/<Guide>.java` (`build/`, `libraries/`, `testing/`, `quality/`), checks are JUnit tests `capaN_...` in `<Guide>Test.java`. Run from `v9_tools/`: `./mvnw test -Dtest=<Guide>Test`. `src/test/.../support/SampleProject` runs real Maven on throwaway projects in `@TempDir` (stdin from NUL + 5-minute timeout: a `cmd` once hung forever; `toRealPath()` because Windows temp dirs come as 8.3 short names and `-pl` then fails). Do not repeat topics already covered elsewhere (Spring guides, v12_jvm): reference them; ROADMAP section 9 lists what is covered where.

### `v15_spring/` (Spring Boot) is different

A Maven multi-module project (Spring Boot 4.1.2, `java.version` 25, compiled with `-Xlint:all`), one module per part (`part1_core`, …, `part6_production`) plus `final_project` (a real app, not a CAPA guide: top-level classes by feature, explained in its README.md); `v15_spring/README.md` is the study guide for the whole section (keep its tables in sync when guides change); each module declares only the starters its part needs. Guides live in `src/main/java/com/practice/spring/<part>/<topic>/<Guide>.java` (same CAPA style, Spanish), one package per guide so component scanning never mixes guides. The checks are JUnit tests, one per CAPA (`capaN_...`), in `src/test/java/.../<Guide>Test.java` — not a `main`. Run from `v15_spring/`: `./mvnw -pl <module> test -Dtest=<Guide>Test` (PowerShell: `.\mvnw.cmd`). Before finishing a Spring guide: its tests pass, the module build shows no compiler warnings, and every quoted message/number comes from a real run. Docker is available (Testcontainers + PostgreSQL work). Exception: `PackagedImageIT` (part6) needs the packaged jar, so it runs with Failsafe: `./mvnw -pl part6_production verify -Dit.test=PackagedImageIT`; full check is `./mvnw clean verify`.

## Guide style (rewritten guides are the reference)

Every guide in the topic folders (`v1_fundamentals/`, `v2_oop/`, `v3_exceptions/`, `v4_generics/`, …) follows the same contract: one concept per file, layered "CAPA" sections that go from zero to technical, real code right below each explanation (example types as static nested classes), no print spam, no commented-out examples except intentional compile errors labeled `ERROR`, and `main` only runs one `check(...)` per layer and prints a single line. Each header carries `Ruta / Anterior / Siguiente` navigation. Before finishing a guide: run `java <file>`, run `javac -Xlint:all -d <tmp> <file>` (must be warning-free), and verify every quoted compiler error message against a scratch file.

## Structure

- **File and folder names are in English; all explanations/comments stay in Spanish.** Top topic folders carry a `vN_` order prefix (`v1_fundamentals` … `v5_collections`; new ones take the next number). Topic folders with more than 3 guides are split into numbered subfolders `partN_name/` (packages can't start with a digit); small folders like `v4_generics/` keep their guides at the top level. E.g. `v2_oop/part1_basics`, `part2_pillars`, `part3_interfaces_and_relationships`, `part4_advanced_types`. Package = folder path (`package v2_oop.part2_pillars;`).
- `v2_oop/part1_basics/modifiers/store` + `sales`: support classes for `AccessModifiers.java`, split into real separate packages on purpose to demonstrate package-private and protected access.
- Root `*.java`: only `Main.java` and `Ejercicios.java` remain (the old study files were fully migrated and deleted). `Ejercicios.java` uses the Java 25+ no-arg `static void main()`.
- `Main.java` (root, no package line): the user's own notes. **Never modify, move or delete it.**
- `Ejercicios.java`: the user's practice space — fix bugs listed in the roadmap, never delete.

## Study plan

`ROADMAP.md` is the checklist of everything still to learn and fix, file by file, in reading order (`v1_fundamentals/` ✅, `v2_oop/` ✅, `v3_exceptions/` ✅, `v4_generics/` ✅, `v5_collections/` ✅, `v6_algorithms/` ✅ (corta), `v7_functional/` ✅, `v8_apis/` ✅, `v9_tools/` ✅ (Maven, Jackson, Git; el resto omitido a propósito, ver ROADMAP), `v10_design/` ✅, `v11_patterns/` ✅, `v12_jvm/` ✅, `v13_concurrency/` ✅, `v14_architecture/` ✅, `v15_spring/` ✅ (Spring Boot: 6 partes + `final_project/`), …). Old files are deleted only once everything in them has a new home — see the migration table at the end of the roadmap. Tick items as their guide files land.

## Editor

`.vscode/settings.json` disables VS Code's Java comment formatter (`java.format.comments.enabled: false`): it reflowed the guides' comment blocks and broke their tables/diagrams. Keep it.
