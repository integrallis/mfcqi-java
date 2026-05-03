plugins {
    java
    `java-library`
    `maven-publish`
    id("com.github.spotbugs") version "6.4.4" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
    jacoco
}

allprojects {
    group = "com.integrallis"

    repositories {
        mavenCentral()
    }
}

val libraryProjects = subprojects

configure(libraryProjects) {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
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
            excludeTags("slow", "benchmark")
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

    tasks.jacocoTestCoverageVerification {
        violationRules {
            rule {
                limit {
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
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

tasks.wrapper {
    gradleVersion = "9.4.1"
    distributionType = Wrapper.DistributionType.ALL
}

tasks.register<Javadoc>("aggregateJavadoc") {
    description = "Generate aggregated Javadoc for all library modules"
    group = "documentation"

    libraryProjects.forEach { proj ->
        proj.afterEvaluate {
            source(proj.the<SourceSetContainer>()["main"].allJava)
            classpath += files(proj.the<SourceSetContainer>()["main"].compileClasspath)
        }
    }
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
