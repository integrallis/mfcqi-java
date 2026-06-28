# Analyzing Kotlin with MFCQI

MFCQI scores **Kotlin** codebases in addition to Java. The same CLI, the same
`[0.0, 1.0]` geometric-mean score, the same native binaries — it auto-detects the language.

> **Status: v1.** Kotlin support is intentionally focused (see [Scope](#what-v1-measures) and
> [Roadmap](#roadmap)). It is production-usable for a complexity-and-secrets quality signal today and
> grows from there.

## Quick start

Install the CLI as usual (see the [README](README.md#installation)) — Kotlin needs no extra setup,
including in the **native binaries** (no JVM, no Kotlin compiler required):

```bash
# Auto-detects Kotlin when the codebase is Kotlin-only:
mfcqi analyze .

# Or be explicit (case-insensitive):
mfcqi analyze path/to/kotlin-project --language kotlin
```

Example:

```
$ mfcqi analyze src/main/kotlin --language kotlin
Analyzing as Kotlin (v1: cyclomatic complexity + secrets).
Score: 0.929

Metric breakdown:
  Cyclomatic Complexity          0.864
  Secrets Exposure               1.000
```

## Language selection

`--language` (alias `--lang`) takes `auto` (default), `java`, or `kotlin`:

| Value | Behavior |
|-------|----------|
| `auto` | Kotlin-only codebases → Kotlin metrics; any Java present → the (richer) Java metric set |
| `kotlin` | Force the Kotlin metric set |
| `java` | Force the Java metric set |

For a **mixed** Java+Kotlin repository, `auto` selects the Java path today (its metric set is
broader). Point `--language kotlin` at the Kotlin source root to score the Kotlin side explicitly.

## What v1 measures

The Kotlin score is the geometric mean of:

- **Cyclomatic Complexity** — average per-function complexity over all `.kt`/`.kts` files. The
  aggregation and normalization curve are identical to the Java metric, so **Kotlin and Java scores
  are directly comparable**.
- **Secrets Exposure** — the language-neutral secrets scan (regex + Shannon entropy), which already
  understands `.kt`/`.kts`.

The geometric mean is non-compensatory: a single weak factor drags the score down.

## How it works (and why it's native-friendly)

Kotlin is parsed with [**kotlinx-ast**](https://github.com/kotlinx/ast)'s ANTLR backend — **not** the
Kotlin compiler. The compiler frontend (`kotlin-compiler-embeddable`) is heavy and reflection-laden
and resists GraalVM native-image; an ANTLR grammar is syntactic-only (exactly what these metrics
need) and compiles cleanly into the native binary. The parser sits behind a single internal seam, so
a higher-fidelity Kotlin-compiler/PSI backend can be added later as a JVM-only mode without changing
the metrics.

## Limitations (v1)

- **Focused metric set** — only Cyclomatic Complexity and Secrets so far (see Roadmap).
- **LLM recommendations are not yet grounded** for Kotlin: the analysis engine receives the metric
  scores but not per-finding tool output, so any file/line references in AI recommendations are
  approximate. Treat them as directional until the grounding work lands.
- **KMP** (Kotlin Multiplatform) source sets and `expect`/`actual` are not yet specially handled —
  files are analyzed as plain Kotlin.

## Using `mfcqi-kotlin` as a library

`mfcqi-kotlin` is published to **Maven Central** like the other modules — no extra repositories
needed. Its Kotlin parser (kotlinx-ast, which is JitPack-only) is **shaded into the artifact**, and
the POM lists only Central-resolvable dependencies, so consumers don't need to add JitPack:

```kotlin
repositories { mavenCentral() }            // no JitPack required
dependencies { implementation("com.integrallis:mfcqi-kotlin:<version>") }
```

```java
double avg  = new KotlinCyclomaticComplexity().extract(path);   // analyze a .kt/.kts tree
double norm = new KotlinCyclomaticComplexity().normalize(avg);  // [0,1]
```

## Roadmap

- Ground LLM recommendations with a Kotlin tool-output collector (real per-function findings).
- Port more metrics: Documentation Coverage (KDoc), Cognitive Complexity, and the OO metrics
  (RFC, DIT, MHF, CBO, LCOM) with Kotlin-aware semantics.
- KMP awareness: per-source-set discovery and `expect`/`actual` de-duplication.
- Optional JVM-only high-fidelity parser mode (Kotlin compiler / PSI) for metrics that need precise
  comment attachment or type resolution.
