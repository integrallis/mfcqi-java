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
    "api"(project(":mfcqi-quality-gates"))
    "implementation"(project(":mfcqi-badge"))
    // Picocli — Apache 2.0, no API key.
    "implementation"("info.picocli:picocli:4.7.6")
    // Jackson for JSON / SARIF rendering.
    "implementation"("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    // JavaParser — ToolOutputCollector walks ASTs to find complexity hotspots for the LLM prompt.
    "implementation"("com.github.javaparser:javaparser-core:3.26.4")
}
