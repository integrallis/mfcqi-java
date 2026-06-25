plugins {
    application
}

description = "Picocli-based CLI: analyze, badge, config, models subcommands"

// The CLI is not published to Maven Central (see root build.gradle.kts). It is shipped
// as a runnable distribution (zip/tar with a bin/mfcqi launcher) attached to the GitHub
// Release. `installDist` produces a local install under build/install/mfcqi/.
application {
    applicationName = "mfcqi"
    mainClass.set("com.integrallis.mfcqi.cli.Main")
}

distributions {
    main {
        // Archive base name -> mfcqi-<version>.zip / .tar (not mfcqi-cli-<version>).
        distributionBaseName.set("mfcqi")
    }
}

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
