plugins {
    `java-gradle-plugin`
    `maven-publish`
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

// java-gradle-plugin creates the pluginMaven + plugin-marker publications; give them full POM
// metadata and stage them into the shared staging-deploy dir so JReleaser signs + deploys them
// to Maven Central alongside the library modules. (Gradle Plugin Portal publishing can be added
// later via com.gradle.plugin-publish — it needs a Portal API key.)
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("MFCQI Gradle Plugin")
            description.set("Analyze, badge, and gate Java/Kotlin projects with MFCQI")
            url.set("https://github.com/integrallis/mfcqi-java")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("bsbodden")
                    name.set("Brian Sam-Bodden")
                    email.set("bsbodden@gmail.com")
                    organization.set("Integrallis Software")
                    organizationUrl.set("https://integrallis.com")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/integrallis/mfcqi-java.git")
                developerConnection.set("scm:git:ssh://git@github.com/integrallis/mfcqi-java.git")
                url.set("https://github.com/integrallis/mfcqi-java")
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile)
        }
    }
}
