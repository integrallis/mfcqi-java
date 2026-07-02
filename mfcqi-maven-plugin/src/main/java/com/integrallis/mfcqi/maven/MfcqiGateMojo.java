package com.integrallis.mfcqi.maven;

import com.integrallis.mfcqi.core.MFCQICalculator;
import com.integrallis.mfcqi.engine.MFCQIDefaults;
import com.integrallis.mfcqi.qualitygates.QualityGateConfig;
import com.integrallis.mfcqi.qualitygates.QualityGateEvaluator;
import com.integrallis.mfcqi.qualitygates.QualityGateResult;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * {@code mfcqi:gate} — evaluates {@code .mfcqi.yaml} quality gates and fails the build on failure.
 */
@Mojo(name = "gate", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class MfcqiGateMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project.basedir}", property = "mfcqi.source")
  private File source;

  @Parameter(property = "mfcqi.parallelism", defaultValue = "1")
  private int parallelism;

  /** Optional explicit gate config; otherwise {@code .mfcqi.yaml} is auto-discovered. */
  @Parameter(property = "mfcqi.gateFile")
  private File gateFile;

  /** Whether a failing gate fails the build. */
  @Parameter(property = "mfcqi.failOnGate", defaultValue = "true")
  private boolean failOnGate;

  @Override
  public void execute() throws MojoFailureException {
    Path path = source.toPath();
    MFCQICalculator calculator = MFCQIDefaults.calculatorFor(path, Math.max(1, parallelism));
    Map<String, Double> detailed = calculator.detailedMetrics(path);

    QualityGateConfig config;
    if (gateFile != null) {
      config = QualityGateConfig.fromFile(gateFile.toPath());
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
      getLog()
          .info(
              String.format(
                  Locale.ROOT,
                  "  [%s] %-28s %.3f (>= %.3f)",
                  metric.passed() ? "PASS" : "FAIL",
                  metric.metric(),
                  metric.actual(),
                  metric.threshold()));
    }

    if (result.passed()) {
      getLog().info("MFCQI quality gate: PASS");
      return;
    }
    String message = "MFCQI quality gate: " + result.failedCount() + " gate(s) failed";
    if (failOnGate) {
      throw new MojoFailureException(message);
    }
    getLog().warn(message);
  }
}
