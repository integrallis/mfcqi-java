plugins {
    `java-gradle-plugin`
}

description = "First-party Gradle plugin for MFCQI — analyze, badge, and gate Java/Kotlin projects"

dependencies {
    "implementation"(project(":mfcqi-engine"))
    "implementation"(project(":mfcqi-quality-gates"))
    "implementation"(project(":mfcqi-badge"))
    "implementation"(project(":mfcqi-core"))
    "testImplementation"(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("mfcqi") {
            id = "com.integrallis.mfcqi"
            implementationClass = "com.integrallis.mfcqi.gradle.MfcqiPlugin"
            displayName = "MFCQI"
            description = "Multi-Factor Code Quality Index — analyze, badge, and gate Java/Kotlin projects"
        }
    }
}

// SpotBugs is noisy on Gradle plugin internals; functional (TestKit) tests run the plugin in a
// forked Gradle so line coverage isn't collected here — skip both gates for this module.
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach { enabled = false }
tasks.matching { it.name == "jacocoTestCoverageVerification" }.configureEach { enabled = false }
