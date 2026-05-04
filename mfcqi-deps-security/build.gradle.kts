description =
    "Dependency CVE metric — wraps OWASP Dependency-Check core (no API key required; uses the public NVD feed)"

dependencies {
    "api"(project(":mfcqi-core"))
    // Jackson for parsing OSV.dev JSON responses (Apache 2.0; no API key required).
    "implementation"("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}
