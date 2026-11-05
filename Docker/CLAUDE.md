# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this directory is

Personal study notes, not a software project. There is no code, no build system, no tests, and no git repository. Two content files:

- **`sistema_y_docker.md`** (previously `linux_containers.md`, then briefly split into `linux_containers.md` + `docker.md`, then merged and renamed on request): a theory-first, beginner-from-zero-to-systems-level walkthrough in four parts — **A** computer/OS fundamentals (hardware, kernel, syscalls, distro/userland, process, thread), **B** the minimum networking needed later (IP, port, socket, client-server, bridge, NAT), **C** virtualization and containers without Docker yet (VMs, namespaces, cgroups, capabilities/seccomp, OverlayFS — "what a container actually is"), and **D** Docker itself (architecture, images, Dockerfile, networking, volumes, Compose, registry, a capstone project), with every Docker concept explicitly tied back to the Part A/B/C mechanism it rests on rather than explained as a black box.
- **`docker_practico.md`**: the hands-on companion — flat numbered sections (1–13, install → CLI → images → Dockerfile → multi-stage → networking → volumes → Compose → debugging → registry → best practices → a guided project → open challenges). Every command is explained the first time it appears (what each flag does, what output to expect), cross-references `sistema_y_docker.md` by section instead of re-explaining the underlying mechanism, and facts that drift over time (install steps, Compose spec conventions) were verified via web search rather than assumed from training data — re-verify before reusing them if much time has passed.

If a future session is asked to touch "the two files" from an old memory of `linux_containers.md`/`docker.md`, that merge is long done — these are the current two, with a different split (theory vs. practice, not fundamentals vs. Docker).

Work here means writing, editing, and fact-checking prose, diagrams, and command examples — not running commands against a real Docker install unless the user is actually following along and asks for that.

## Conventions to follow when editing notes

**Language: Spanish.** All note content is in Spanish. Technical terms, command names, and flags stay in English/as-is (`namespace`, `cgroup`, `seccomp`, `task_struct`, `syscall`, `docker run`, `--rm`). Match that mix.

**Structure differs by file.** `sistema_y_docker.md` runs Parts A→D, each a numbered progression (`## A1.`, `## D7.`, ...) from the most physical/foundational to the most abstract; a later section references an earlier one by number/part instead of re-explaining it. `docker_practico.md` runs a flat numbered list of practical sections, each closing with a **🔧 Practica esto** block (and the more advanced ones with open-ended challenges instead). Don't duplicate content across the two files — link to the other file's section instead.

**ASCII diagrams must align.** Every diagram uses box-drawing characters (`┌─┐│└┘•→»`) with each box's edges in a fixed column, verified programmatically when built (a Python script comparing each `┌`/`└`/`│` line's length — both files currently sit at zero width mismatches). After any edit inside a box, re-run that kind of check; don't eyeball it.

**No em dashes, and don't overuse colons either.** Both were explicit corrections from the user after earlier drafts leaned on them as a crutch (using "—" or "X: Y" as the default sentence joiner for nearly everything). Vary the construction instead: separate sentences, "que"/"donde"/"sino que" clauses, commas, semicolons, parentheses for asides. Reserve a colon for where it's genuinely the right punctuation (introducing a short concrete list, a direct definition) — not as the default way to connect any two clauses. A paragraph with two or more colons doing the same "label: explanation" job back to back is the failure mode to catch.

**No analogies — direct technical language instead.** Explanations are for someone with no OS/networking/Docker background, but the explanation itself is always the real technical mechanism (concrete numbers, real syscall/struct/field names, real command output, step-by-step "what happens underneath" walkthroughs) — never a metaphor (no buildings/apartments, no molds/cookies, no car engines, no offices) standing in for the mechanism. If a concept seems to need an analogy to land, add a concrete example or diagram instead.

**Never let a role-noun read as a proper name.** A past draft wrote "el proceso servidor" and the user flagged it as ambiguous (reads like "the process named Servidor"). When introducing a role/label for something generic ("servidor", "cliente"), use an explicit tag the first time ("un proceso, al que llamaremos el servidor, ..." or similar), not a bare compound noun — check any new paragraph introducing a role this way before it lands.

**When correcting one of the above, fix the paragraph once, holistically — don't patch the same paragraph repeatedly.** Several corrections in a row landed on the same B5 paragraph (dash removed → colon-reduced → ambiguity fix → ambiguity fix bolted on as an extra sentence) and the user called out that stacking narrow patches was degrading it instead of improving it. When a correction is requested, re-read the paragraph's original clean version (or the surrounding text's established style, e.g. the parenthetical role-labeling already used elsewhere) and rewrite from there in one pass, rather than editing only the specific phrase just flagged.

**Precision over simplification.** The notes deliberately correct common misconceptions and distinguish things most explanations blur together:

- A container is not a kernel object. There is no `struct container`; the word names a *coordinated sum* of mechanisms applied to an ordinary Linux process.
- The three mechanisms answer different questions: namespaces = what a process **sees**, cgroups = how much it **can use**, capabilities/seccomp = what it is **allowed to do**. Cgroups and capabilities are explicitly *not* namespaces.
- Each of the six mechanisms has a different nature — PID NS is a *reference* (same `task_struct`, different ID per viewer), mount NS and network NS are *new structures*, cgroup is a *real directory* in one host-wide tree, caps/seccomp are a *bitmask plus a BPF filter*. Don't flatten these into "six namespaces".
- Container vs. VM hinges on one thing: a VM runs its own kernel, a container uses the host's.
- "Servidor"/"cliente" are roles a process takes by which syscalls it calls (`bind()`+`listen()` vs. `connect()`), not machine types or process names.

When adding or rewording material, preserve these distinctions. Losing them is the failure mode these notes exist to prevent.
