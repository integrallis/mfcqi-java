package com.integrallis.mfcqi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrallis.mfcqi.core.MFCQICalculator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class MFCQIDefaultsTest {

  @Test
  void calculator_includesEveryCoreMetric(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Tiny.java"),
        "/** doc */ public class Tiny { /** doc */ public int v() { return 1; } }");

    MFCQICalculator calc = MFCQIDefaults.calculator();
    Map<String, Double> detail = calc.detailedMetrics(tmp);
    // 10 core metrics + mfcqi_score, all must be present.
    assertThat(detail.keySet())
        .contains(
            "Cyclomatic Complexity",
            "Cognitive Complexity",
            "Halstead Volume",
            "Maintainability Index",
            "Code Duplication",
            "Documentation Coverage",
            "security",
            "Dependency Security",
            "Secrets Exposure",
            "Code Smell Density",
            "mfcqi_score");
  }

  @Test
  void calculator_addsOoMetricsForStrongOoCodebase(@TempDir Path tmp) throws Exception {
    // A codebase with classes, fields, inheritance — should classify as STRONG_OO or MIXED_OO
    // and pick up the OO metric suite.
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Service.java"),
        "public class Service {\n"
            + "  private final Repository repo;\n"
            + "  public Service(Repository r) { this.repo = r; }\n"
            + "  public String process() { return repo.find(); }\n"
            + "}\n"
            + "class ServiceImpl extends Service {\n"
            + "  public ServiceImpl(Repository r) { super(r); }\n"
            + "  public String getName() { return \"x\"; }\n"
            + "}");

    Map<String, Double> detail = MFCQIDefaults.calculator().detailedMetrics(tmp);
    // At least RFC should be present (WEAK_OO and above all include it).
    assertThat(detail.keySet()).contains("rfc");
  }

  @Test
  void calculator_producesScoreInUnitInterval(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("X.java"), "public class X { public int v() { return 1; } }");
    double score = MFCQIDefaults.calculator().calculate(tmp);
    assertThat(score).isBetween(0.0, 1.0);
  }
}
