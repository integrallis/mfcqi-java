package com.integrallis.mfcqi.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class LackOfCohesionOfMethodsTest {

  private final LackOfCohesionOfMethods metric = new LackOfCohesionOfMethods();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.50);
    assertThat(metric.getName()).isEqualTo("Lack of Cohesion of Methods");
  }

  @Test
  void normalize_followsPythonPiecewise() {
    assertThat(metric.normalize(1.0)).isEqualTo(1.0);
    assertThat(metric.normalize(3.0)).isCloseTo(0.7, within(1e-9));
    assertThat(metric.normalize(6.0)).isCloseTo(0.4, within(1e-9));
    assertThat(metric.normalize(12.0)).isEqualTo(0.0);
  }

  @Test
  void extract_emptyCodebaseHasZero(@TempDir Path tmp) {
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_singleMethodClassHasZeroLcom(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("A.java"), "public class A { public int v() { return 1; } }");
    // Python: <=1 method => return 0.0 (perfect cohesion).
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_methodsSharingFieldFormOneComponent(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Cohesive.java"),
        "public class Cohesive {\n"
            + "  private int counter;\n"
            + "  public void inc() { counter = counter + 1; }\n"
            + "  public int get() { return counter; }\n"
            + "}");
    // Both methods touch `counter` -> connected -> 1 component -> LCOM = 1.
    assertThat(metric.extract(tmp)).isCloseTo(1.0, within(1e-9));
  }

  @Test
  void extract_methodsTouchingDisjointFieldsFormSeparateComponents(@TempDir Path tmp)
      throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Split.java"),
        "public class Split {\n"
            + "  private int alpha;\n"
            + "  private int beta;\n"
            + "  public int aOne() { return alpha; }\n"
            + "  public int aTwo() { return alpha + 1; }\n"
            + "  public int bOne() { return beta; }\n"
            + "  public int bTwo() { return beta - 1; }\n"
            + "}");
    // Two disjoint groups: {aOne, aTwo} share alpha; {bOne, bTwo} share beta. LCOM = 2.
    assertThat(metric.extract(tmp)).isCloseTo(2.0, within(1e-9));
  }
}
