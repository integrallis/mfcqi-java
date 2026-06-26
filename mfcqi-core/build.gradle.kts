description = "Metric framework, MFCQI calculator (geometric mean), source-file utilities"

// Single-source the build version: generate a properties resource from project.version
// (which comes from gradle.properties) and bundle it so Version.current() can read it at runtime.
val versionResourceDir = layout.buildDirectory.dir("generated/version")
val projectVersion = project.version.toString()

val generateVersionResource by
    tasks.registering {
      inputs.property("version", projectVersion)
      outputs.dir(versionResourceDir)
      doLast {
        val file = versionResourceDir.get().file("mfcqi-version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$projectVersion\n")
      }
    }

// Pass the task provider (not just the dir) so the generate task becomes a dependency of every
// consumer of the resources source set — processResources, sourcesJar, tests, etc.
sourceSets["main"].resources.srcDir(generateVersionResource)
