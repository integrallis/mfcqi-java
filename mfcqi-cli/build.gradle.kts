description = "Picocli-based CLI: analyze, badge, config, models subcommands"

dependencies {
    "implementation"(project(":mfcqi-core"))
    "implementation"(project(":mfcqi-metrics"))
    "implementation"(project(":mfcqi-duplication"))
    "implementation"(project(":mfcqi-secrets"))
    "implementation"(project(":mfcqi-smells"))
    "implementation"(project(":mfcqi-security"))
    "implementation"(project(":mfcqi-deps-security"))
    "implementation"(project(":mfcqi-analysis"))
    "implementation"(project(":mfcqi-quality-gates"))
    "implementation"(project(":mfcqi-badge"))
    // Picocli — Apache 2.0, no API key. Annotation processor is recommended for help-output
    // generation but optional; we keep the build lean by skipping it.
    "implementation"("info.picocli:picocli:4.7.6")
    // Jackson for JSON output of analyze results (already a transitive dep).
    "implementation"("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}
