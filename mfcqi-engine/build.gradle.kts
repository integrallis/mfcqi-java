description = "MFCQI calculator assembly — the default metric registry for Java, Kotlin, and mixed"

dependencies {
    // Exposed so consumers (CLI, Gradle plugin) get the full metric set transitively.
    "api"(project(":mfcqi-core"))
    "api"(project(":mfcqi-metrics"))
    "api"(project(":mfcqi-kotlin"))
    "api"(project(":mfcqi-duplication"))
    "api"(project(":mfcqi-secrets"))
    "api"(project(":mfcqi-smells"))
    "api"(project(":mfcqi-security"))
    "api"(project(":mfcqi-deps-security"))
}
