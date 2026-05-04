description =
    "Code-smell detectors — ports of PyExamine (production smells) and AST test smells, adapted to the Java AST"

dependencies {
    "api"(project(":mfcqi-core"))
    "implementation"("com.github.javaparser:javaparser-core:3.26.4")
}
