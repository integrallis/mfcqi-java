plugins {
    // Auto-provisions the JDK toolchain the Kotlin module needs (JDK 21) on any machine/CI runner,
    // so a JDK-25-only environment doesn't fail mfcqi-kotlin's compile.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "mfcqi-java"

include("mfcqi-core")
include("mfcqi-metrics")
include("mfcqi-duplication")
include("mfcqi-secrets")
include("mfcqi-smells")
include("mfcqi-security")
include("mfcqi-deps-security")
include("mfcqi-analysis")
include("mfcqi-quality-gates")
include("mfcqi-badge")
include("mfcqi-kotlin")
include("mfcqi-engine")
include("mfcqi-gradle-plugin")
include("mfcqi-maven-plugin")
include("mfcqi-cli")

buildCache {
    local {
        isEnabled = true
    }
}
