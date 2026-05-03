# mfcqi-java

Java port of the [mfcqi](https://github.com/integrallis/mfcqi) Python library — a Multi-Factor Code Quality Index for Java codebases. Combines 10–15 evidence-based metrics into a single 0.0–1.0 quality score using a non-compensatory geometric mean.

> **Status:** early development. Targeting parity with the Python reference implementation at `~/Code/mfcqi`.

## Requirements

- Java 11 or newer (compiled with `--release 11`)
- Gradle 9.4.1 (use the bundled `./gradlew`)

## Build

```bash
./gradlew build           # compile, test, spotbugs, jacoco
./gradlew spotlessApply   # format code
./gradlew spotlessCheck   # verify formatting (CI gate)
```

## Modules

| Module | Purpose |
|---|---|
| `mfcqi-core` | `Metric` interface, `MFCQICalculator` (geometric mean), `ParadigmDetector` |
| `mfcqi-metrics` | Cyclomatic, Cognitive, Halstead, Maintainability, Documentation, Duplication, RFC, DIT, MHF, CBO, LCOM |
| `mfcqi-duplication` | Hash-based block duplication detection (ported from Python) |
| `mfcqi-secrets` | Regex catalog + Shannon entropy secret detection (ported from Python `detect-secrets`) |
| `mfcqi-smells` | Code-smell detectors (ported from PyExamine + AST test smells, adapted for Java AST) |
| `mfcqi-security` | SpotBugs + FindSecBugs SAST integration |
| `mfcqi-deps-security` | OWASP Dependency-Check CVE scanning |
| `mfcqi-analysis` | Optional LLM analysis engine (Anthropic / OpenAI / Ollama) |
| `mfcqi-quality-gates` | YAML-driven quality gate evaluator |
| `mfcqi-badge` | shields.io badge generation |
| `mfcqi-cli` | Picocli-based CLI: `analyze`, `badge`, `config`, `models` |

## License

TBD (matching the Python sister project).
