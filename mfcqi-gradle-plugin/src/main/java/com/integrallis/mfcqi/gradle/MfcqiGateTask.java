package com.integrallis.mfcqi.gradle;

import com.integrallis.mfcqi.core.MFCQICalculator;
import com.integrallis.mfcqi.engine.MFCQIDefaults;
import com.integrallis.mfcqi.qualitygates.QualityGateConfig;
import com.integrallis.mfcqi.qualitygates.QualityGateEvaluator;
import com.integrallis.mfcqi.qualitygates.QualityGateResult;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * {@code mfcqiGate} — evaluates {@code .mfcqi.yaml} quality gates and fails the build on failure.
 */
public abstract class MfcqiGateTask extends DefaultTask {

  @Internal
  public abstract DirectoryProperty getSource();

  @Input
  public abstract Property<Integer> getParallelism();

  @Optional
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getGateFile();

  @Input
  public abstract Property<Boolean> getFailOnGate();

  @TaskAction
  public void run() {
    Path path = getSource().get().getAsFile().toPath();
    MFCQICalculator calculator = MFCQIDefaults.calculatorFor(path, getParallelism().get());
    Map<String, Double> detailed = calculator.detailedMetrics(path);

    QualityGateConfig config;
    if (getGateFile().isPresent()) {
      config = QualityGateConfig.fromFile(getGateFile().get().getAsFile().toPath());
    } else {
      config =
          QualityGateEvaluator.findQualityGateConfig(path)
              .map(QualityGateConfig::fromFile)
              .orElseGet(QualityGateConfig::fromDefaults);
    }

    double overall = detailed.getOrDefault("mfcqi_score", 0.0);
    Map<String, Double> metricScores = new LinkedHashMap<>(detailed);
    metricScores.remove("mfcqi_score");
    Map<String, Object> analysisResult = new LinkedHashMap<>();
    analysisResult.put("mfcqi_score", overall);
    analysisResult.put("metric_scores", metricScores);

    QualityGateResult result = new QualityGateEvaluator(config).evaluate(analysisResult);

    for (QualityGateResult.MetricResult metric : result.metricResults()) {
      getLogger()
          .lifecycle(
              String.format(
                  Locale.ROOT,
                  "  [%s] %-28s %.3f (>= %.3f)",
                  metric.passed() ? "PASS" : "FAIL",
                  metric.metric(),
                  metric.actual(),
                  metric.threshold()));
    }

    if (result.passed()) {
      getLogger().lifecycle("MFCQI quality gate: PASS");
      return;
    }
    String message = "MFCQI quality gate: " + result.failedCount() + " gate(s) failed";
    if (Boolean.TRUE.equals(getFailOnGate().get())) {
      throw new GradleException(message);
    }
    getLogger().warn(message);
  }
}
