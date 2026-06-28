# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Kotlin support (v1)**: MFCQI now scores Kotlin codebases. A new `mfcqi-kotlin` module parses
  Kotlin via [kotlinx-ast](https://github.com/kotlinx/ast) (ANTLR — no Kotlin compiler, so it works
  in the **native binaries** too) and contributes a Kotlin **Cyclomatic Complexity** metric whose
  curve matches the Java metric; the language-neutral **Secrets** scan is reused. The CLI gains
  `--language java|kotlin|auto` (alias `--lang`, case-insensitive) and **auto-detects** Kotlin-only
  codebases. Kotlin ships seamlessly in every CLI distribution (native binaries, Homebrew, Scoop,
  install script, JVM zip). See [KOTLIN.md](KOTLIN.md).

### Changed
- `MFCQICalculator.Builder.analyzableSource(Predicate<Path>)` makes the empty-codebase gate
  pluggable (default unchanged — Java source detection), enabling the Kotlin calculator.

## [0.3.0] - 2026-06-27

### Added
- **GraalVM native binaries**: the CLI now builds to a self-contained native executable (no JRE
  required) via the GraalVM native-image Gradle plugin. A CI matrix (`native.yml`) produces
  per-platform binaries — `mfcqi-linux-x86_64`, `mfcqi-macos-aarch64`, `mfcqi-windows-x86_64.exe` —
  and attaches them to the release. Reflection/resource config is
  captured under `mfcqi-cli/.../META-INF/native-image/`. Startup drops from ~140ms to ~8ms.
- **Distribution channels** for the native binary: a `curl | sh` install script (`install.sh`), an
  auto-updated **Homebrew tap** (`brew install integrallis/tap/mfcqi`), and a **Scoop bucket**
  (`scoop install mfcqi`). CI renders the formula/manifest from `packaging/` templates and pushes
  them to the tap/bucket repos on each release.

## [0.2.0] - 2026-06-26

### Added
- `analyze --timeout <seconds>` and the `MFCQI_TIMEOUT` / `CQI_LLM_TIMEOUT` env vars to raise the
  LLM request timeout (the 60s default is often too short for local Ollama models).
- A progress spinner while the LLM call runs (animated on a TTY, a single static line when piped),
  so the CLI no longer sits silently while a local model loads/generates.

### Fixed
- `analyze --provider ollama --model <name>` no longer demands an API key: a bare model name is now
  routed to Ollama (the `ollama:` prefix is applied automatically) instead of a key-based provider.
- The version is now single-sourced from `gradle.properties` (a generated resource read via
  `Version.current()`); `--version` and the SARIF report no longer carry stale `0.1.0-SNAPSHOT`
  literals, and the SARIF repository URLs point at `integrallis/mfcqi-java`.

### Changed
- README corrected for accuracy: real terminal output sample, `--recommendations` default is 50
  (not 10), removed the unsupported `OLLAMA_HOST` note, documented `--timeout`.

## [0.1.0] - 2026-06-24

First public release of the MFCQI Java edition.

### Added
- `MFCQICalculator` producing a single 0.0–1.0 quality score via a geometric mean of
  evidence-based metrics (`mfcqi-core`).
- Complexity and OO metrics — cyclomatic, cognitive, Halstead, maintainability,
  documentation, RFC, DIT, MHF, CBO, LCOM (`mfcqi-metrics`).
- Hash-based duplication detection (`mfcqi-duplication`).
- Secret detection via curated regex catalog + Shannon-entropy scanning (`mfcqi-secrets`).
- Architectural, design, implementation, and test smell detectors built on JavaParser
  (`mfcqi-smells`).
- Source-level security analysis (`mfcqi-security`).
- Dependency CVE scanning via OSV.dev with an offline fallback (`mfcqi-deps-security`).
- Optional LLM analysis engine for recommendations — Anthropic / OpenAI / Ollama
  (`mfcqi-analysis`).
- YAML-driven quality gate evaluator (`mfcqi-quality-gates`).
- shields.io badge generation (`mfcqi-badge`).
- Picocli CLI with `analyze`, `badge`, `config`, `models`, and `quality-gate` commands,
  shipped as a runnable distribution (`mfcqi-cli`).
- Self-score badge (`.github/badges/mfcqi.json`) and badge-refresh workflow.

### Fixed
- `mfcqi analyze .` returned 0.0 because the leading `.` of a relative path was treated as a
  hidden directory in source discovery; current-dir/parent-dir tokens are now ignored.
- Metric fairness for Java (parity with the Python reference): Halstead operator/operand
  classification corrected and the volume normalization recalibrated for Java's token model;
  Maintainability Index fed a radon-scale volume; RFC counts only scoped, same-class calls; CBO
  and the high-coupling smell exclude JDK/library types; the secrets scanner no longer self-matches
  its own alphabet constants and now skips Java test sources; documentation coverage excludes
  `@Override` methods (their contract is inherited). The library's self-score is 0.89.

[0.3.0]: https://github.com/integrallis/mfcqi-java/releases/tag/v0.3.0
[0.2.0]: https://github.com/integrallis/mfcqi-java/releases/tag/v0.2.0
[0.1.0]: https://github.com/integrallis/mfcqi-java/releases/tag/v0.1.0
