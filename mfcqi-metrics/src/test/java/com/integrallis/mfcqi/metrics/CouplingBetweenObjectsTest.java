package com.integrallis.mfcqi.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class CouplingBetweenObjectsTest {

  private final CouplingBetweenObjects metric = new CouplingBetweenObjects();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.65);
    assertThat(metric.getName()).isEqualTo("Coupling Between Objects");
  }

  @Test
  void normalize_followsPythonPiecewise() {
    assertThat(metric.normalize(0.0)).isEqualTo(1.0);
    assertThat(metric.normalize(9.0)).isCloseTo(0.7, within(1e-9));
    assertThat(metric.normalize(20.0)).isCloseTo(0.4, within(1e-9));
    assertThat(metric.normalize(40.0)).isEqualTo(0.0);
  }

  @Test
  void extract_emptyCodebaseHasZero(@TempDir Path tmp) {
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_classWithNoCouplingHasZero(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Standalone.java"),
        "public class Standalone {\n" + "  public int v() { return 1; }\n" + "}");
    // Only built-ins (int) referenced — filtered out -> CBO = 0.
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_externalReferencesIncreaseCoupling(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Hub.java"),
        "public class Hub {\n"
            + "  public Worker worker;\n"
            + "  public Logger logger;\n"
            + "  public Repository repo;\n"
            + "  public void process(Worker w, Logger l) {}\n"
            + "}");
    // Distinct external types: Worker, Logger, Repository (built-ins filtered) -> CBO = 3
    assertThat(metric.extract(tmp)).isCloseTo(3.0, within(1e-9));
  }

  @Test
  void extract_averagesAcrossClasses(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Mixed.java"),
        "public class A {\n"
            + "  public Foo foo;\n"
            + "  public Bar bar;\n"
            + "}\n"
            + "class B {\n"
            + "  public int v() { return 1; }\n"
            + "}");
    // A: {Foo, Bar} = 2; B: {} = 0; Avg = 1.0
    assertThat(metric.extract(tmp)).isCloseTo(1.0, within(1e-9));
  }
}
