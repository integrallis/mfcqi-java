description =
    "Metric implementations: cyclomatic, cognitive, halstead, maintainability, documentation, OO (RFC, DIT, MHF, CBO, LCOM)"

dependencies {
    "api"(project(":mfcqi-core"))
    // JavaParser — Apache 2.0, no API key. Used for AST analysis across complexity, cognitive,
    // documentation, and OO metrics.
    "implementation"("com.github.javaparser:javaparser-core:3.26.4")
}
