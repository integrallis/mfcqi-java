plugins {
    id("org.gradlex.maven-plugin-development") version "1.0.3"
}

description = "Apache Maven plugin for MFCQI — analyze, badge, and gate Java/Kotlin projects"

mavenPlugin {
    goalPrefix.set("mfcqi")
}

dependencies {
    "implementation"(project(":mfcqi-engine"))
    "implementation"(project(":mfcqi-quality-gates"))
    "implementation"(project(":mfcqi-badge"))
    "implementation"(project(":mfcqi-core"))
    "compileOnly"("org.apache.maven:maven-plugin-api:3.9.9")
    "compileOnly"("org.apache.maven.plugin-tools:maven-plugin-annotations:3.13.1")
}

// Mojos are best tested with the maven-plugin-testing-harness (a separate effort); the functional
// behaviour is shared with the CLI/Gradle plugin via mfcqi-engine, so skip the line-coverage gate.
tasks.matching { it.name == "jacocoTestCoverageVerification" }.configureEach { enabled = false }

// SpotBugs is noisy on Maven Mojo field-injection patterns; skip it for this module.
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach { enabled = false }

// The descriptor task reads the runtime classpath, which includes mfcqi-core's generated version
// resource; declare the dependency so Gradle 9's strict task-dependency validation is satisfied.
tasks.named("generateMavenPluginDescriptor") {
    dependsOn(":mfcqi-core:generateVersionResource")
}
