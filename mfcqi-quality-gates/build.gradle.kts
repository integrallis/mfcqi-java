description = "Quality-gate evaluator — reads .mfcqi.yaml gates and scores the analysis result"

dependencies {
    "api"(project(":mfcqi-core"))
    // SnakeYAML for parsing .mfcqi.yaml — Apache 2.0, no API key.
    "implementation"("org.yaml:snakeyaml:2.3")
}
