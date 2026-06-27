# MFCQI for Java - Multi-Factor Code Quality Index

[![MFCQI Score](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/integrallis/mfcqi-java/main/.github/badges/mfcqi.json)](https://github.com/integrallis/mfcqi-java)
[![Java 11+](https://img.shields.io/badge/java-11+-blue.svg)](https://adoptium.net/)
[![Gradle](https://img.shields.io/badge/Gradle-9.4.1-02303A.svg?logo=gradle)](https://gradle.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Sister project: mfcqi (Python)](https://img.shields.io/badge/sibling-mfcqi%20(Python)-3776AB?logo=python&logoColor=white)](https://github.com/bsbodden/mfcqi)

![logo](https://raw.githubusercontent.com/bsbodden/mfcqi/main/docs/mfcqi.png)

**MFCQI** (Multi-Factor Code Quality Index) is a comprehensive code quality analysis tool that produces a single quality
score (0.0-1.0) by combining multiple evidence-based metrics. This is the Java edition; the [Python edition](https://github.com/bsbodden/mfcqi) is its sibling — same formula, same family, different platform.

## Why MFCQI?

Traditional code quality tools provide dozens of metrics without a unified quality score. MFCQI provides:

- **Single Score**: One number (0.0-1.0) that represents overall code quality
- **Evidence-Based**: Combines proven metrics using a research-backed approach
- **AI-Enhanced**: Optional LLM integration for intelligent recommendations
- **Fast Analysis**: Efficient static analysis of Java codebases via JavaParser
- **No Gaming**: Geometric mean formula prevents gaming individual metrics

## Quick Start

### Installation

The CLI ships as a **GraalVM native binary** — a single self-contained executable with **no Java
required**. Pick whichever method suits you:

#### Homebrew (macOS & Linux)

```bash
brew install integrallis/tap/mfcqi
```

#### Scoop (Windows)

```powershell
scoop bucket add integrallis https://github.com/integrallis/scoop-bucket
scoop install mfcqi
```

#### Install script (macOS & Linux)

```bash
curl -fsSL https://raw.githubusercontent.com/integrallis/mfcqi-java/main/install.sh | sh
# Installs to ~/.local/bin by default; override with MFCQI_INSTALL_DIR / MFCQI_VERSION.
```

#### Manual download

Grab the binary for your platform from the
[latest release](https://github.com/integrallis/mfcqi-java/releases/latest) —
`mfcqi-linux-x86_64`, `mfcqi-macos-aarch64`, `mfcqi-macos-x86_64`, or `mfcqi-windows-x86_64.exe`:

```bash
curl -fsSL -o mfcqi https://github.com/integrallis/mfcqi-java/releases/latest/download/mfcqi-linux-x86_64
chmod +x mfcqi && ./mfcqi analyze .
```

> A JVM distribution (`mfcqi-<version>.zip`/`.tar`, requiring a JRE 11+) is also attached to each
> release for platforms without a native binary.

#### Build from source

```bash
# Clone and build the multi-module project
git clone https://github.com/integrallis/mfcqi-java.git
cd mfcqi-java

# Build everything (compile, test, SpotBugs, JaCoCo)
./gradlew build

# Build only the CLI distribution (installs into build/install/mfcqi)
./gradlew :mfcqi-cli:installDist

# The launcher will be at:
#   mfcqi-cli/build/install/mfcqi/bin/mfcqi
```

> The CLI is distributed only via GitHub Releases / source build — it is **not** published to Maven
> Central. The library modules (`mfcqi-core`, `mfcqi-metrics`, …) *are* on Maven Central for
> programmatic use.

You can also publish the modules to your local Maven repository to consume them as libraries from another Gradle/Maven project:

```bash
./gradlew publishToMavenLocal
```

### Basic Usage

```bash
# Analyze current directory (metrics only)
mfcqi analyze .

# Analyze a specific source root
mfcqi analyze src/main/java

# Analyze a single file
mfcqi analyze src/main/java/com/example/Service.java

# Analyze with AI recommendations from a cloud model (reads ANTHROPIC_API_KEY / OPENAI_API_KEY)
mfcqi analyze . --model claude-sonnet-4-5

# Use a local Ollama model — no API key required
mfcqi analyze . --provider ollama --model qwen2.5-coder:7b

# Raise the LLM request timeout for slow local models (default 60s)
mfcqi analyze . --provider ollama --model qwen2.5-coder:7b --timeout 300

# Change how many recommendations to request (default 50)
mfcqi analyze . --model claude-sonnet-4-5 --recommendations 15

# Output JSON for CI/CD integration
mfcqi analyze . --format json --output report.json

# Fail CI if quality is below threshold
mfcqi analyze . --min-score 0.75

# Generate a badge for your project
mfcqi badge .                                   # shows shields.io URL
mfcqi badge . -f json -o .github/badges/mfcqi.json
```

### Badge Generation

MFCQI can generate quality badges for your README:

```bash
# Generate a shields.io badge URL
mfcqi badge .

# Generate JSON for dynamic badges
mfcqi badge . -f json -o badge.json

# Get markdown instructions
mfcqi badge . -f markdown
```

The badge automatically uses color coding:

- 🟢 **Green** (≥0.80): Excellent quality
- 🟡 **Yellow** (≥0.60): Good quality
- 🟠 **Orange** (≥0.40): Fair quality
- 🔴 **Red** (<0.40): Poor quality

## The MFCQI Formula

MFCQI uses a Drake Equation-inspired geometric mean to ensure all quality factors matter:

  ```txt
  MFCQI = (M₁ × M₂ × ... × Mₙ)^(1/n)
  ```

Where n is the number of metrics applied (typically 15 for normal Java codebases).

### Metrics

Java is an object-oriented language by construction, so every metric below applies to every analysis.

#### Code Quality

- **Cyclomatic Complexity**: Number of linearly independent paths through the code
- **Cognitive Complexity**: How difficult the code is to understand
- **Halstead Volume**: Program complexity from unique operators and operands
- **Maintainability Index**: Composite of complexity, volume, and lines of code
- **Code Duplication**: Duplicate code blocks across the codebase
- **Documentation Coverage**: Javadoc coverage for public types and methods
- **Code Smell Density**: Architectural, design, implementation, and test smells via JavaParser AST analysis

#### Object-Oriented Design

- **RFC (Response for Class)**: Methods that can execute in response to a message
- **DIT (Depth of Inheritance Tree)**: Maximum inheritance path from a class to its root
- **MHF (Method Hiding Factor)**: Ratio of private/protected methods to total methods
- **CBO (Coupling Between Objects)**: Number of classes a class is coupled to
- **LCOM (Lack of Cohesion of Methods)**: Connected components in the method-attribute graph

#### Security

- **Security (SAST)**: Vulnerability density via SpotBugs + FindSecBugs with CVSS scoring and CWE mapping
- **Dependency Security (SCA)**: Maven/Gradle dependencies scanned for known CVEs via the OSV.dev API
- **Secrets Exposure**: Hardcoded credentials detected via a curated regex catalog and Shannon-entropy scoring

## LLM Integration

MFCQI integrates with LLM providers (Anthropic, OpenAI, Ollama) for intelligent recommendations.

### Configuration

MFCQI reads provider credentials from the standard environment variables — the same ones the official Anthropic, OpenAI, and Ollama tools use:

```bash
export ANTHROPIC_API_KEY=...   # for claude-* models
export OPENAI_API_KEY=...      # for gpt-* models
# Ollama runs locally and needs no key; point at a non-default daemon with the
# --ollama-endpoint option per invocation.
```

Get your API keys from:

- OpenAI: <https://platform.openai.com/api-keys>
- Anthropic: <https://console.anthropic.com/settings/keys>

The LLM request timeout defaults to 60 seconds. Local models often need longer (they may load several
GB on the first call), so raise it with `--timeout <seconds>` or the `MFCQI_TIMEOUT` environment
variable if you see a timeout error.

### Selecting a provider and model

```bash
mfcqi analyze . --provider anthropic --model claude-sonnet-4-5
mfcqi analyze . --provider openai    --model gpt-4o
mfcqi analyze . --provider ollama    --model codellama:7b --ollama-endpoint http://localhost:11434
```

### Managing Models

```bash
# List available Ollama models
mfcqi models list

# Pull a new Ollama model (streams real progress from /api/pull)
mfcqi models pull llama3.2

# Recommend a model for your hardware
mfcqi models recommend
```

## Features

### Terminal Output

- Plain, readable score and metric breakdown
- A progress spinner while the LLM call runs (on an interactive terminal), so the CLI isn't silent
  while a local model loads and generates
- Prioritized, severity-tagged recommendations

### Multiple Output Formats

- **Terminal**: Formatted, color-coded output
- **JSON**: For programmatic access and dashboards
- **HTML**: For reports and dashboards
- **Markdown**: For documentation
- **SARIF**: For native ingestion by GitHub Code Scanning and other SAST tooling

### CI/CD Integration

```yaml
# GitHub Actions example
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: 21

- name: Check Code Quality
  run: |
    ./gradlew :mfcqi-cli:installDist
    ./mfcqi-cli/build/install/mfcqi/bin/mfcqi analyze src/main/java \
      --min-score 0.7 \
      --format json \
      --output mfcqi-report.json
```

### Graceful Degradation

- Works without API keys (metrics-only mode)
- Falls back to local Ollama models when available
- Clear messaging about available features

## Security Metric Details

The Security metric evaluates code vulnerability density using industry-standard approaches:

- **CVSS Scoring**: Each finding is scored using CVSS v3.1 (0-10 scale) based on severity and confidence
- **CWE Mapping**: SpotBugs/FindSecBugs categories are mapped to specific CWE (Common Weakness Enumeration) IDs
- **Critical Checks**: Categories such as SQL injection, command injection, and hardcoded credentials are never skipped
- **Vulnerability Density**: Calculated as CVSS points per source line of code (SLOC)
- **Normalization**: Uses an exponential decay function for a smooth scoring gradient
- **Configurable Thresholds**: Default threshold of 0.03 (3 CVSS points per 100 lines) balances security and practicality

## Modules

`mfcqi-java` is a multi-module Gradle project so each concern can be consumed independently:

| Module                | Purpose                                                                                            |
|-----------------------|----------------------------------------------------------------------------------------------------|
| `mfcqi-core`          | `Metric` interface, `MFCQICalculator` (geometric mean), source-file utilities                      |
| `mfcqi-metrics`       | Cyclomatic, Cognitive, Halstead, Maintainability, Documentation, Duplication, RFC/DIT/MHF/CBO/LCOM |
| `mfcqi-duplication`   | Hash-based block duplication detection                                                             |
| `mfcqi-secrets`       | Curated regex catalog + Shannon-entropy secret detection                                           |
| `mfcqi-smells`        | Architectural, design, implementation, and test smell detectors built on JavaParser                |
| `mfcqi-security`      | SpotBugs + FindSecBugs SAST integration                                                            |
| `mfcqi-deps-security` | OSV.dev / OWASP Dependency-Check CVE scanning                                                      |
| `mfcqi-analysis`      | Optional LLM analysis engine (Anthropic / OpenAI / Ollama)                                         |
| `mfcqi-quality-gates` | YAML-driven quality gate evaluator                                                                 |
| `mfcqi-badge`         | shields.io badge generation                                                                        |
| `mfcqi-cli`           | Picocli-based CLI: `analyze`, `badge`, `config`, `models`, `quality-gate`                          |

## Development

### Prerequisites

- JDK 11 or newer (the build is configured with `--release 11`; JDK 21+ recommended for development)
- Gradle 9.4.1 (use the bundled `./gradlew` wrapper)

### Setup Development Environment

```bash
# Clone the repository
git clone https://github.com/integrallis/mfcqi-java.git
cd mfcqi-java

# Set up local secrets used by the integration tests that exercise the
# real LLM providers. End users do not need this file — production
# invocations of `mfcqi` read OPENAI_API_KEY / ANTHROPIC_API_KEY from the
# environment directly.
cp .env.example .env
# Edit .env and add your API keys

# Compile, test, run SpotBugs, and produce JaCoCo reports
./gradlew build

# Format code (Spotless)
./gradlew spotlessApply

# Verify formatting (CI gate)
./gradlew spotlessCheck

# Run the test suite for a single module
./gradlew :mfcqi-metrics:test
```

## Expected Score Ranges

Based on the metrics used, typical MFCQI scores for different code quality levels:

| Quality Level | MFCQI Range | Characteristics                                              |
|---------------|-------------|--------------------------------------------------------------|
| Excellent     | 0.80 - 1.00 | Low complexity, well-documented, tested, minimal duplication |
| Good          | 0.60 - 0.79 | Moderate complexity, decent documentation, some tests        |
| Fair          | 0.40 - 0.59 | Higher complexity, sparse documentation, limited testing     |
| Poor          | 0.00 - 0.39 | Very complex, poorly documented, untested code               |

Example CLI usage (actual terminal output):

```text
➜ mfcqi analyze src/main/java --provider ollama --model qwen2.5-coder:7b
MFCQI analysis: src/main/java
Score: 0.522

Metric breakdown:
  Cyclomatic Complexity          0.868
  Cognitive Complexity           0.913
  Halstead Volume                0.909
  Maintainability Index          0.714
  Code Duplication               0.871
  Documentation Coverage         0.000
  security                       0.309
  Dependency Security            1.000
  Secrets Exposure               0.000
  Code Smell Density             0.910
  rfc                            0.914
  dit                            1.000
  mhf                            0.000
  Coupling Between Objects       0.850
  Lack of Cohesion of Methods    0.600

Recommendations:
  1. [CRITICAL] Fix Documentation Coverage in PaymentService.java:21: zero Javadoc on public API.
  2. [CRITICAL] Fix Secrets Exposure in PaymentService.java:21: hardcoded credentials present.
  3. [HIGH] Fix Use of Weak Hash Function in PaymentService.java:21: MD5 is collision-vulnerable.
```

Other output formats (`--format json|html|markdown|sarif`) render the same data for dashboards,
documentation, or ingestion by GitHub Code Scanning.

## Research Foundation

MFCQI is grounded in established code quality research, with calibration appropriate to the language under analysis. The Java edition shares its formula and metric set with the [Python edition](https://github.com/bsbodden/mfcqi); thresholds and normalizations are tuned for Java's syntactic and idiomatic profile.

### Foundational Research

#### Complexity Metrics

- **Cyclomatic Complexity**: McCabe (1976) - "A Complexity Measure"
- **Cognitive Complexity**: Campbell (2018) - SonarSource validation
- **Halstead Metrics**: Halstead (1977) - "Elements of Software Science"
- **Maintainability Index**: Coleman et al. (1994) - "Using Metrics to Evaluate Software System Maintainability"

#### Object-Oriented Metrics

- **RFC, DIT, CBO, LCOM**: Chidamber & Kemerer (1994) - "A Metrics Suite for Object Oriented Design"
- **MHF, AHF**: Brito e Abreu & Carapuça (1994)
- **Prykhodko et al. (2021)**: "A Statistical Evaluation of The Depth of Inheritance Tree Metric for Open-Source Applications Developed in Java" — DIT 2-5 recommended at the class level

#### Security Metrics

- **CVSS (Common Vulnerability Scoring System)**: FIRST.org (2019) - "CVSS v3.1 Specification"
- **CWE (Common Weakness Enumeration)**: MITRE Corporation (2024) - "CWE List Version 4.13"
- **Vulnerability Density**: Alhazmi & Malaiya (2005) - "Quantitative Vulnerability Assessment of Systems Software"

### Methodology

MFCQI combines proven metrics using:

- **Geometric mean** aggregation (non-compensatory)
- **Java-specific** threshold calibration
- **Security-conscious** evaluation with CVSS scoring
- **Evidence-based** normalizations validated against representative Java codebases

## Dependencies and Libraries

MFCQI for Java relies on a small set of well-maintained, OSS-only libraries that require no API keys to operate:

### Core Analysis

| Library                         | Purpose                                                | Coordinates                                   |
|---------------------------------|--------------------------------------------------------|-----------------------------------------------|
| **JavaParser**                  | AST parsing for source-level metrics                   | `com.github.javaparser:javaparser-core`       |
| **SpotBugs**                    | Static analysis engine                                 | `com.github.spotbugs:spotbugs`                |
| **FindSecBugs**                 | Security-focused SpotBugs plugin                       | `com.h3xstream.findsecbugs:findsecbugs-plugin`|
| **OSV.dev API**                 | Dependency vulnerability data (no API key required)    | `https://api.osv.dev/v1/query`                |
| **OWASP Dependency-Check**      | Optional richer SCA backend                            | `org.owasp:dependency-check-core`             |

### LLM & Configuration

| Library            | Purpose                                      | Coordinates                                   |
|--------------------|----------------------------------------------|-----------------------------------------------|
| **Pebble**         | Jinja2-compatible templating for LLM prompts | `io.pebbletemplates:pebble`                   |
| **Jackson**        | JSON / SARIF serialization                   | `com.fasterxml.jackson.core:jackson-databind` |
| **JDK HttpClient** | HTTP client for LLM and OSV.dev calls        | `java.net.http` (JDK 11+)                     |
| **SnakeYAML**      | YAML-driven quality gates                    | `org.yaml:snakeyaml`                          |

### CLI & Utilities

| Library         | Purpose                              | Coordinates                          |
|-----------------|--------------------------------------|--------------------------------------|
| **Picocli**     | Command-line interface framework     | `info.picocli:picocli`               |
| **SLF4J**       | Logging facade                       | `org.slf4j:slf4j-api`                |

### Build & Quality Tooling

| Tool             | Purpose                              |
|------------------|--------------------------------------|
| **Gradle 9.4.1** | Multi-module build                   |
| **Spotless**     | Code formatting (google-java-format) |
| **SpotBugs**     | Static analysis (build gate)         |
| **JaCoCo**       | Coverage reporting                   |

## Sister Project

The [Python edition of MFCQI](https://github.com/bsbodden/mfcqi) implements the same formula and metric set for Python codebases. Both editions share research foundations, scoring philosophy, and CLI ergonomics; pick the edition that matches the language you want to analyze.

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Links

- [GitHub Repository](https://github.com/integrallis/mfcqi-java)
- [Issue Tracker](https://github.com/integrallis/mfcqi-java/issues)
- [Sister project: mfcqi (Python)](https://github.com/bsbodden/mfcqi)

---

Made with ❤️ by BSB
