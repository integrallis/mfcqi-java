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
include("mfcqi-cli")

buildCache {
    local {
        isEnabled = true
    }
}
