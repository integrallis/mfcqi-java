package com.integrallis.mfcqi.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class SecretsExposureMetricTest {

  private final SecretsExposureMetric metric = new SecretsExposureMetric();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.85);
    assertThat(metric.getName()).isEqualTo("Secrets Exposure");
  }

  @Test
  void normalize_severePenaltyCurve() {
    // Verbatim from Python: 0->1.0, 1->0.3, 2-3->0.1, 4+->0.0.
    assertThat(metric.normalize(0.0)).isEqualTo(1.0);
    assertThat(metric.normalize(1.0)).isEqualTo(0.3);
    assertThat(metric.normalize(2.0)).isEqualTo(0.1);
    assertThat(metric.normalize(3.0)).isEqualTo(0.1);
    assertThat(metric.normalize(4.0)).isEqualTo(0.0);
    assertThat(metric.normalize(20.0)).isEqualTo(0.0);
  }

  @Test
  void extract_emptyCodebaseHasNoSecrets(@TempDir Path tmp) {
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_findsAwsAccessKey(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Config.java"),
        "public class Config {\n"
            + "  public static final String AWS_KEY = \"AKIAIOSFODNN7EXAMPLE\";\n"
            + "}");
    assertThat(metric.extract(tmp)).isGreaterThanOrEqualTo(1.0);
  }

  @Test
  void extract_findsPrivateKeyHeader(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Keys.java"),
        "public class Keys {\n"
            + "  public static final String PEM = \"-----BEGIN RSA PRIVATE KEY-----\\n...\";\n"
            + "}");
    assertThat(metric.extract(tmp)).isGreaterThanOrEqualTo(1.0);
  }

  @Test
  void extract_skipsTestAndExampleFiles(@TempDir Path tmp) throws Exception {
    // Skip patterns: Python (test_, example., .example, fixture, /tests/) PLUS the JVM additions
    // (/test/, /src/test/, *Test.java, *Tests.java, *IT.java). A fake AWS key in a real production
    // file must be counted; the same key in test/example files must be skipped.
    Path mainDir = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        mainDir.resolve("Prod.java"),
        "public class Prod { String key = \"AKIAIOSFODNN7EXAMPLE\"; }");

    // Maven/Gradle test source root + *Test.java suffix -> skipped (the old port missed this).
    Path testDir = Files.createDirectories(tmp.resolve("src/test/java"));
    Files.writeString(
        testDir.resolve("FooTest.java"),
        "public class FooTest {\n" + "  String key = \"AKIAIOSFODNN7EXAMPLE\";\n" + "}");
    // Python /tests/ convention -> skipped.
    Path testsDir = Files.createDirectories(tmp.resolve("src/tests"));
    Files.writeString(
        testsDir.resolve("Helper.java"),
        "public class Helper { String key = \"AKIAIOSFODNN7EXAMPLE\"; }");
    // example. substring -> skipped.
    Path exampleFile = Files.createDirectories(tmp.resolve("src")).resolve("Config.example.java");
    Files.writeString(
        exampleFile, "public class Config { String key = \"AKIAIOSFODNN7EXAMPLE\"; }");

    long count = metric.extract(tmp).longValue();
    // Only the production file (src/main/java/Prod.java) is counted; the three test/example files
    // are all skipped.
    assertThat(count).isEqualTo(1L);
  }

  @Test
  void shouldSkip_matchesPythonSubstrings() {
    assertThat(SecretsExposureMetric.shouldSkip("/path/to/test_helper.py")).isTrue();
    assertThat(SecretsExposureMetric.shouldSkip("/path/to/config.example.json")).isTrue();
    assertThat(SecretsExposureMetric.shouldSkip("/path/to/secret.example")).isTrue();
    assertThat(SecretsExposureMetric.shouldSkip("/repo/fixtures/sample.json")).isTrue();
    assertThat(SecretsExposureMetric.shouldSkip("/repo/tests/Helper.java")).isTrue();
    assertThat(SecretsExposureMetric.shouldSkip("/repo/src/main/Real.java")).isFalse();
  }
}
