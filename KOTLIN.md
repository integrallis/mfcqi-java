# Analyzing Kotlin with MFCQI

MFCQI analyzes Java, Kotlin, and mixed Java/Kotlin repositories with the same 15-metric output
contract and the same `[0.0, 1.0]` geometric-mean score.

## Quick start

Install the CLI as described in the [README](README.md#installation). Kotlin analysis needs no JVM
or Kotlin compiler when using a native binary.

```bash
# Auto-detect Java, Kotlin, or a mixed repository.
mfcqi analyze .

# Force a mode when analyzing a selected source root.
mfcqi analyze src/main/kotlin --language kotlin
mfcqi analyze . --language mixed
```

`--language` (alias `--lang`) accepts:

| Value | Behavior |
|-------|----------|
| `auto` | Select Java, Kotlin, or mixed analysis from discovered source files |
| `kotlin` | Analyze `.kt` files with the Kotlin metric implementations |
| `java` | Analyze `.java` files with the Java metric implementations |
| `mixed` | Combine corresponding Java and Kotlin source metrics |

Badge generation and the standalone `quality-gate` command use the same automatic language
selection.

## Metric contract

Kotlin reports the same metric keys as Java:

- Cyclomatic Complexity
- Cognitive Complexity
- Halstead Volume
- Maintainability Index
- Code Duplication
- Documentation Coverage
- security
- Code Smell Density
- rfc
- dit
- mhf
- Coupling Between Objects
- Lack of Cohesion of Methods
- Dependency Security
- Secrets Exposure

The source metrics use the same normalization curves and weights as their Java counterparts.
Dependency Security and Secrets Exposure are shared language-neutral metrics. In mixed mode, MFCQI
averages each corresponding Java and Kotlin normalized source metric, then evaluates the two shared
metrics once.

## Implementation

Kotlin parsing and duplication detection use
[PMD 7's Kotlin support](https://docs.pmd-code.org/latest/pmd_languages_kotlin.html), which is based
on the official Kotlin grammar. MFCQI calculates its metric formulas over PMD's typed AST and uses
PMD CPD for duplication detection. A fingerprinted immutable analysis cache is shared by concurrently
executing metrics, so `--parallelism` does not parse the repository once per metric.

PMD does not embed the Kotlin compiler, and the implementation is verified as part of the GraalVM
native CLI build. Kotlin compiler/PSI and Detekt are not runtime dependencies.

## Scope and limitations

- Source discovery includes `.kt` files. `.kts` scripts are excluded because PMD's Kotlin source
  frontend does not parse Gradle or general Kotlin script grammar.
- Metrics that require semantic type resolution use syntactic Kotlin equivalents. For example, CBO
  uses declared type references and DIT follows declared inheritance.
- Kotlin Multiplatform source sets are analyzed as ordinary Kotlin source. MFCQI does not yet
  de-duplicate `expect`/`actual` declarations.
- The LLM tool-output collector is JavaParser-based. Pure Kotlin analysis sends metric scores but
  does not yet provide Kotlin-specific per-finding file and line context to the recommendation
  model.

## Library use

`mfcqi-kotlin` is published to Maven Central and depends on `pmd-kotlin`; no additional repository
is required.

```kotlin
repositories { mavenCentral() }
dependencies { implementation("com.integrallis:mfcqi-kotlin:<version>") }
```

```java
double average = new KotlinCyclomaticComplexity().extract(path);
double normalized = new KotlinCyclomaticComplexity().normalize(average);

List<Metric<?>> fullSuite = KotlinMetrics.all();
```

## Remaining work

- Add a Kotlin-specific tool-output collector for grounded LLM recommendations.
- Add KMP source-set awareness and `expect`/`actual` de-duplication.
- Expand real-repository calibration fixtures as Kotlin syntax and PMD evolve.
