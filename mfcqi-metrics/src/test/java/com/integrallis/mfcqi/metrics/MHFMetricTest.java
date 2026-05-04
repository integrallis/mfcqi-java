package com.integrallis.mfcqi.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class MHFMetricTest {

  private final MHFMetric metric = new MHFMetric();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.55);
    assertThat(metric.getName()).isEqualTo("mhf");
  }

  @Test
  void normalize_isIdentityClampedToUnitInterval() {
    assertThat(metric.normalize(0.0)).isEqualTo(0.0);
    assertThat(metric.normalize(0.5)).isEqualTo(0.5);
    assertThat(metric.normalize(1.0)).isEqualTo(1.0);
    assertThat(metric.normalize(-0.1)).isEqualTo(0.0);
    assertThat(metric.normalize(1.5)).isEqualTo(1.0);
  }

  @Test
  void extract_emptyCodebaseHasZero(@TempDir Path tmp) {
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_publicOnlyClassYieldsZero(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Open.java"),
        "public class Open { public int a(){return 1;} public int b(){return 2;} }");
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_halfPrivateYieldsHalf(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Half.java"),
        "public class Half {\n"
            + "  public int a(){return 1;}\n"
            + "  private int b(){return 2;}\n"
            + "}");
    assertThat(metric.extract(tmp)).isCloseTo(0.5, within(1e-9));
  }

  @Test
  void extract_takesMaxAcrossClasses(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Mixed.java"),
        "public class Mixed {\n"
            + "  public int a(){return 1;}\n"
            + "  public int b(){return 2;}\n"
            + "}\n"
            + "class Hidden {\n"
            + "  private int x(){return 1;}\n"
            + "  private int y(){return 2;}\n"
            + "  public int z(){return 3;}\n"
            + "}");
    // Mixed -> 0/2 = 0.0; Hidden -> 2/3. Max = 2/3.
    assertThat(metric.extract(tmp)).isCloseTo(2.0 / 3.0, within(1e-9));
  }
}
