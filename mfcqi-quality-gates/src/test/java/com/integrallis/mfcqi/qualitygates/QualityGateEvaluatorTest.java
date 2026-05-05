package com.integrallis.mfcqi.qualitygates;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class QualityGateEvaluatorTest {

  @Test
  void fromDefaults_matchesPythonReference() {
    QualityGateConfig cfg = QualityGateConfig.fromDefaults();
    assertThat(cfg.overallGates()).containsEntry("mfcqi_score", 0.6);
    assertThat(cfg.metricGates())
        .containsEntry("security", 0.8)
        .containsEntry("cyclomatic_complexity", 0.7)
        .containsEntry("cognitive_complexity", 0.7)
        .containsEntry("maintainability_index", 0.7);
  }

  @Test
  void parse_readsYamlIntoTypedConfig() {
    String yaml =
        "quality_gates:\n"
            + "  overall:\n"
            + "    mfcqi_score: 0.7\n"
            + "  metrics:\n"
            + "    security: 0.8\n"
            + "    cyclomatic_complexity: 0.65\n";
    QualityGateConfig cfg =
        QualityGateConfig.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    assertThat(cfg.overallGates()).containsEntry("mfcqi_score", 0.7);
    assertThat(cfg.metricGates())
        .containsEntry("security", 0.8)
        .containsEntry("cyclomatic_complexity", 0.65);
  }

  @Test
  void evaluate_passesWhenAllThresholdsMet() {
    QualityGateConfig cfg = new QualityGateConfig(map("mfcqi_score", 0.6), map("security", 0.7));
    Map<String, Object> result = analysisResult(0.85, map("security", 0.9));
    QualityGateResult r = new QualityGateEvaluator(cfg).evaluate(result);
    assertThat(r.passed()).isTrue();
    assertThat(r.overallResult()).isTrue();
    assertThat(r.failedCount()).isZero();
    assertThat(r.passedCount()).isEqualTo(2); // overall + security
  }

  @Test
  void evaluate_failsWhenOverallScoreBelowThreshold() {
    QualityGateConfig cfg =
        new QualityGateConfig(map("mfcqi_score", 0.7), java.util.Collections.emptyMap());
    QualityGateResult r =
        new QualityGateEvaluator(cfg)
            .evaluate(analysisResult(0.5, java.util.Collections.emptyMap()));
    assertThat(r.passed()).isFalse();
    assertThat(r.overallResult()).isFalse();
    assertThat(r.failedCount()).isEqualTo(1);
  }

  @Test
  void evaluate_failsWhenIndividualMetricBelowThreshold() {
    QualityGateConfig cfg = new QualityGateConfig(map("mfcqi_score", 0.5), map("security", 0.9));
    Map<String, Object> result = analysisResult(0.7, map("security", 0.5));
    QualityGateResult r = new QualityGateEvaluator(cfg).evaluate(result);
    assertThat(r.passed()).isFalse();
    assertThat(r.overallResult()).isTrue();
    assertThat(r.metricResults()).hasSize(1);
    assertThat(r.metricResults().get(0).passed()).isFalse();
  }

  @Test
  void evaluate_overallSecondaryGateAlsoChecked() {
    // Python: a non-mfcqi_score key in overall_gates that fails -> overall_result false.
    QualityGateConfig cfg =
        new QualityGateConfig(
            mapOf("mfcqi_score", 0.5, "security", 0.9), java.util.Collections.emptyMap());
    QualityGateResult r =
        new QualityGateEvaluator(cfg).evaluate(analysisResult(0.7, map("security", 0.5)));
    assertThat(r.overallResult()).isFalse();
    assertThat(r.passed()).isFalse();
  }

  @Test
  void findQualityGateConfig_returnsMfcqiYamlWhenPresent(@TempDir Path tmp) throws Exception {
    Files.writeString(tmp.resolve(".mfcqi.yaml"), "quality_gates: {overall: {mfcqi_score: 0.5}}");
    Optional<Path> found = QualityGateEvaluator.findQualityGateConfig(tmp);
    assertThat(found).isPresent();
    assertThat(found.get().getFileName().toString()).isEqualTo(".mfcqi.yaml");
  }

  @Test
  void findQualityGateConfig_fallsBackToMfcqiGatesYaml(@TempDir Path tmp) throws Exception {
    Files.writeString(
        tmp.resolve(".mfcqi-gates.yaml"), "quality_gates: {overall: {mfcqi_score: 0.5}}");
    Optional<Path> found = QualityGateEvaluator.findQualityGateConfig(tmp);
    assertThat(found).isPresent();
    assertThat(found.get().getFileName().toString()).isEqualTo(".mfcqi-gates.yaml");
  }

  @Test
  void findQualityGateConfig_emptyForNothing(@TempDir Path tmp) {
    assertThat(QualityGateEvaluator.findQualityGateConfig(tmp)).isEmpty();
  }

  private static Map<String, Object> analysisResult(double overall, Map<String, Double> scores) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("mfcqi_score", overall);
    m.put("metric_scores", scores);
    return m;
  }

  private static Map<String, Double> map(String key, double value) {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put(key, value);
    return m;
  }

  private static Map<String, Double> mapOf(String k1, double v1, String k2, double v2) {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put(k1, v1);
    m.put(k2, v2);
    return m;
  }
}
