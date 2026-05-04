description =
    "Security SAST metric — source-level Java analog of Bandit's pattern checks (weak crypto, command injection, deserialization, path traversal, insecure RNG, XXE, SSL trust-all)"

dependencies {
    "api"(project(":mfcqi-core"))
    // JavaParser for AST-driven pattern matching — same library used by mfcqi-metrics. No
    // bytecode required; matches Bandit's source-only model from the Python reference.
    "implementation"("com.github.javaparser:javaparser-core:3.26.4")
}
