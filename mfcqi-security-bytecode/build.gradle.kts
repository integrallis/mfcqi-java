description = "Bytecode security scanner (SpotBugs + FindSecBugs) for the MFCQI security metric"

dependencies {
    // Reuses SecurityScanner / SecurityFinding + the CVSS scoring in mfcqi-security.
    "api"(project(":mfcqi-security"))
    "implementation"(project(":mfcqi-core"))
    "implementation"("com.github.spotbugs:spotbugs:4.9.8")
    "implementation"("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")
}

// This module pulls SpotBugs (native-image hostile); it is consumed ONLY by the Gradle/Maven
// plugins, never by mfcqi-cli / the GraalVM native path.
// SpotBugs analyzing SpotBugs-using code is meta-noisy; functional tests exercise the scanner.
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach { enabled = false }
tasks.matching { it.name == "jacocoTestCoverageVerification" }.configureEach { enabled = false }
