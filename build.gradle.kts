plugins {
    java
    `java-library`
    jacoco
    id("com.github.spotbugs") version "6.4.4" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
}

allprojects {
    group = "com.integrallis"

    repositories {
        mavenCentral()
        // mfcqi-kotlin's Kotlin parser (kotlinx-ast) is published on JitPack.
        maven("https://jitpack.io")
    }
}

// Published to Maven Central as libraries via this shared configuration. The CLI ships as a native
// binary / runnable distribution instead. mfcqi-kotlin is excluded *here* because it needs a bespoke
// SHADED publication (it bundles the JitPack-only kotlinx-ast jars) — that is defined in
// mfcqi-kotlin/build.gradle.kts and still stages into build/staging-deploy for JReleaser.
val nonPublished = setOf("mfcqi-cli", "mfcqi-kotlin")
val libraryProjects = subprojects.filterNot { it.name in nonPublished }

// ---------------------------------------------------------------------------
// Common configuration applied to ALL modules (libraries + CLI)
// ---------------------------------------------------------------------------
configure(subprojects) {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "jacoco")

    java {
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        // --release 11 enforces both Java 11 bytecode AND blocks accidental use of
        // post-Java-11 APIs at compile time. Works with any JDK 11+ build environment.
        options.release.set(11)
        options.compilerArgs.addAll(listOf(
            "-parameters",
            "-Xlint:all",
            "-Xlint:-processing",
            "-Xlint:-serial",
            "-Werror"
        ))
    }

    tasks.withType<Test> {
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
        maxParallelForks = Runtime.getRuntime().availableProcessors()
    }

    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("slow", "benchmark", "llm-integration")
        }
    }

    tasks.register<Test>("llmIntegrationTest") {
        description =
            "Run LLM integration tests (require ANTHROPIC_API_KEY/OPENAI_API_KEY in env or .env)"
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform {
            includeTags("llm-integration")
        }
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    tasks.register<Test>("unitTest") {
        description = "Run only unit tests"
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform {
            includeTags("unit")
        }
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    tasks.register<Test>("slowTest") {
        description = "Run slow tests (large codebases, real-world fixtures)"
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform {
            includeTags("slow")
        }
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    tasks.withType<Javadoc> {
        val javadocOptions = options as StandardJavadocDocletOptions
        javadocOptions.addBooleanOption("Xdoclint:all,-missing", true)
        javadocOptions.addBooleanOption("html5", true)
        isFailOnError = false
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat("1.35.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
        excludeFilter.set(file("${rootProject.projectDir}/spotbugs-exclude.xml"))
    }

    tasks.named("spotbugsTest") {
        enabled = false
    }

    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        reports {
            xml.required = true
            html.required = true
        }
    }

    // Uniform 80% instruction-coverage gate across every module.
    tasks.jacocoTestCoverageVerification {
        dependsOn(tasks.test)
        violationRules {
            rule {
                limit {
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }

    // Enforce the coverage gate as part of `check` (and therefore `build`).
    tasks.named("check") {
        dependsOn(tasks.jacocoTestCoverageVerification)
    }

    dependencies {
        "implementation"("org.slf4j:slf4j-api:2.0.16")

        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testImplementation"("org.assertj:assertj-core:3.27.2")
        "testImplementation"("org.mockito:mockito-core:5.15.2")
        "testImplementation"("org.mockito:mockito-junit-jupiter:5.15.2")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testRuntimeOnly"("ch.qos.logback:logback-classic:1.5.15")
    }
}

// ---------------------------------------------------------------------------
// Publishing: only the library modules are published to Maven Central.
// Each module stages into a single shared directory (build/staging-deploy)
// so JReleaser can sign and upload the whole set as one Central deployment.
// ---------------------------------------------------------------------------
configure(libraryProjects) {
    apply(plugin = "maven-publish")

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set(provider { project.description ?: "MFCQI — ${project.name}" })
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
        }
        repositories {
            maven {
                name = "staging"
                url = uri(rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile)
            }
        }
    }
}

tasks.wrapper {
    gradleVersion = "9.4.1"
    distributionType = Wrapper.DistributionType.ALL
}

val aggregateJavadoc = tasks.register<Javadoc>("aggregateJavadoc") {
    description = "Generate aggregated Javadoc for all library modules"
    group = "documentation"

    setDestinationDir(layout.buildDirectory.dir("docs/javadoc/aggregate").get().asFile)

    (options as StandardJavadocDocletOptions).apply {
        title = "mfcqi-java ${project.version} API"
        windowTitle = "mfcqi-java ${project.version}"
        author(true)
        version(true)
        use(true)
        splitIndex(true)
        links("https://docs.oracle.com/en/java/javase/11/docs/api/")
        addStringOption("Xdoclint:-missing", "-quiet")
    }

    isFailOnError = false
}

// Wire the per-module sources/classpath into the aggregate task only after every
// project has been evaluated. Doing this inside the task body via afterEvaluate is
// illegal once configuration has progressed and breaks `./gradlew tasks` at the root.
gradle.projectsEvaluated {
    aggregateJavadoc.configure {
        libraryProjects.forEach { proj ->
            source(proj.the<SourceSetContainer>()["main"].allJava)
            classpath += files(proj.the<SourceSetContainer>()["main"].compileClasspath)
        }
    }
}
