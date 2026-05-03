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
}
