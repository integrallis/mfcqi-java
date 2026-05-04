package com.integrallis.mfcqi.depssecurity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class DependencyExtractorTest {

  @Test
  void extractAll_findsMavenAndGradleDependencies(@TempDir Path tmp) throws Exception {
    Files.writeString(
        tmp.resolve("pom.xml"),
        "<?xml version=\"1.0\"?><project><dependencies>"
            + "<dependency>"
            + "<groupId>org.slf4j</groupId>"
            + "<artifactId>slf4j-api</artifactId>"
            + "<version>2.0.16</version>"
            + "</dependency>"
            + "</dependencies></project>");

    Path sub = Files.createDirectories(tmp.resolve("module"));
    Files.writeString(
        sub.resolve("build.gradle.kts"),
        "dependencies {\n"
            + "  implementation(\"com.fasterxml.jackson.core:jackson-databind:2.18.2\")\n"
            + "  testImplementation(\"org.junit.jupiter:junit-jupiter:5.11.4\")\n"
            + "}");

    List<Dependency> deps = DependencyExtractor.extractAll(tmp);
    assertThat(deps)
        .containsExactlyInAnyOrder(
            new Dependency("Maven", "org.slf4j:slf4j-api", "2.0.16"),
            new Dependency("Maven", "com.fasterxml.jackson.core:jackson-databind", "2.18.2"),
            new Dependency("Maven", "org.junit.jupiter:junit-jupiter", "5.11.4"));
  }

  @Test
  void parseMavenPom_skipsPropertyVersions(@TempDir Path tmp) throws Exception {
    Path pom =
        Files.writeString(
            tmp.resolve("pom.xml"),
            "<project><dependencies>"
                + "<dependency><groupId>g</groupId><artifactId>a</artifactId><version>${ver}</version></dependency>"
                + "<dependency><groupId>g2</groupId><artifactId>a2</artifactId><version>1.0.0</version></dependency>"
                + "</dependencies></project>");
    List<Dependency> deps = DependencyExtractor.parseMavenPom(pom);
    assertThat(deps).containsExactly(new Dependency("Maven", "g2:a2", "1.0.0"));
  }

  @Test
  void parseGradleBuildScript_handlesGroovyDsl(@TempDir Path tmp) throws Exception {
    Path script =
        Files.writeString(
            tmp.resolve("build.gradle"),
            "dependencies {\n"
                + "  api 'com.google.guava:guava:33.4.0-jre'\n"
                + "  implementation 'org.apache.commons:commons-lang3:3.17.0'\n"
                + "}");
    List<Dependency> deps = DependencyExtractor.parseGradleBuildScript(script);
    assertThat(deps)
        .containsExactlyInAnyOrder(
            new Dependency("Maven", "com.google.guava:guava", "33.4.0-jre"),
            new Dependency("Maven", "org.apache.commons:commons-lang3", "3.17.0"));
  }

  @Test
  void extractAll_emptyDirectoryYieldsNoDeps(@TempDir Path tmp) {
    assertThat(DependencyExtractor.extractAll(tmp)).isEmpty();
  }
}
