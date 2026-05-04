package com.integrallis.mfcqi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class SecurityMetricTest {

  private final SecurityMetric metric = new SecurityMetric();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.7);
    assertThat(metric.getName()).isEqualTo("security");
  }

  @Test
  void normalize_zeroDensityReturnsOne() {
    assertThat(metric.normalize(0.0)).isEqualTo(1.0);
  }

  @Test
  void normalize_appliesExponentialDecay() {
    // Python: exp(-density / 0.03). Spot-check density=0.03 -> exp(-1) ~= 0.368.
    assertThat(metric.normalize(0.03)).isCloseTo(Math.exp(-1.0), within(1e-9));
    assertThat(metric.normalize(0.09)).isCloseTo(Math.exp(-3.0), within(1e-9));
  }

  @Test
  void calculateCvss_highSeverityHighConfidence() {
    // Python: HIGH base 8.0 * confidence 1.0 = 8.0, clamped within HIGH bounds.
    assertThat(
            SecurityMetric.calculateCvss(
                SecurityFinding.Severity.HIGH, SecurityFinding.Confidence.HIGH))
        .isCloseTo(8.0, within(1e-9));
  }

  @Test
  void calculateCvss_clampsLowSeverityToBandRange() {
    // LOW base 2.0 * LOW factor 0.6 = 1.2 -> clamped within [0.1, 3.9].
    double v =
        SecurityMetric.calculateCvss(SecurityFinding.Severity.LOW, SecurityFinding.Confidence.LOW);
    assertThat(v).isBetween(0.1, 3.9);
    assertThat(v).isCloseTo(1.2, within(1e-9));
  }

  @Test
  void adjustForCwe_boostsCriticalCwesByTwentyPercent() {
    assertThat(SecurityMetric.adjustForCwe(8.0, "CWE-78")).isCloseTo(8.0 * 1.2, within(1e-9));
    assertThat(SecurityMetric.adjustForCwe(8.0, "CWE-1")).isCloseTo(8.0, within(1e-9));
  }

  @Test
  void adjustForCwe_capsAtTen() {
    // Anything * 1.2 that exceeds 10.0 must be clamped.
    assertThat(SecurityMetric.adjustForCwe(9.5, "CWE-78")).isEqualTo(10.0);
  }

  @Test
  void extract_emptyCodebaseHasZeroDensity(@TempDir Path tmp) {
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_findsWeakHashAndCommandInjection(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Bad.java"),
        "import java.security.MessageDigest;\n"
            + "public class Bad {\n"
            + "  public void f(String userInput) throws Exception {\n"
            + "    MessageDigest md = MessageDigest.getInstance(\"MD5\");\n"
            + "    Runtime.getRuntime().exec(\"sh -c \" + userInput);\n"
            + "  }\n"
            + "}");
    double density = metric.extract(tmp);
    // Two findings -> non-zero density.
    assertThat(density).isGreaterThan(0.0);
    assertThat(metric.lastFindings()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(metric.lastFindings()).extracting(SecurityFinding::testId).contains("B303", "B605");
  }

  @Test
  void extract_findsSqlInjectionViaConcatenation(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Db.java"),
        "import java.sql.Statement;\n"
            + "public class Db {\n"
            + "  public void f(Statement st, String name) throws Exception {\n"
            + "    st.executeQuery(\"select * from users where name = '\" + name + \"'\");\n"
            + "  }\n"
            + "}");
    metric.extract(tmp);
    assertThat(metric.lastFindings()).extracting(SecurityFinding::testId).contains("B608");
  }

  @Test
  void extract_findsXxeWhenFactoryNotHardened(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Xml.java"),
        "import javax.xml.parsers.DocumentBuilderFactory;\n"
            + "public class Xml {\n"
            + "  public DocumentBuilderFactory unsafe() {\n"
            + "    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();\n"
            + "    return f;\n"
            + "  }\n"
            + "  public DocumentBuilderFactory safe() throws Exception {\n"
            + "    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();\n"
            + "    f.setFeature(\"http://apache.org/xml/features/disallow-doctype-decl\", true);\n"
            + "    return f;\n"
            + "  }\n"
            + "}");
    metric.extract(tmp);
    long xxeFindings =
        metric.lastFindings().stream().filter(f -> f.testId().equals("B313")).count();
    // Only the unsafe() method should be flagged; safe() calls setFeature() and is exempt.
    assertThat(xxeFindings).isEqualTo(1);
  }

  @Test
  void extract_findsInsecureRandom(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("R.java"),
        "import java.util.Random;\n" + "public class R { Random r = new Random(); }");
    metric.extract(tmp);
    assertThat(metric.lastFindings()).extracting(SecurityFinding::testId).contains("B311");
  }
}
