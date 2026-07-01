package com.integrallis.mfcqi.cli.commands;

import com.integrallis.mfcqi.cli.MFCQIDefaults;
import com.integrallis.mfcqi.core.MFCQICalculator;
import com.integrallis.mfcqi.qualitygates.QualityGateConfig;
import com.integrallis.mfcqi.qualitygates.QualityGateEvaluator;
import com.integrallis.mfcqi.qualitygates.QualityGateResult;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code mfcqi quality-gate} — runs analysis and evaluates the result against {@code .mfcqi.yaml}
 * or {@code .mfcqi-gates.yaml}, exiting non-zero on failure. Mirrors the {@code --quality-gate}
 * flag of the Python {@code analyze} command.
 */
@Command(
    name = "quality-gate",
    mixinStandardHelpOptions = true,
    description = "Evaluate the codebase against quality-gate thresholds.")
public final class QualityGateCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Path to the codebase directory.", arity = "0..1")
  Path path = Path.of(".");

  @Option(
      names = {"--config"},
      description = "Path to a quality-gate config (.yaml). Defaults to .mfcqi.yaml lookup.")
  Path configPath;

  @Override
  public Integer call() {
    QualityGateConfig config;
    if (configPath != null) {
      config = QualityGateConfig.fromFile(configPath);
    } else {
      Optional<Path> discovered = QualityGateEvaluator.findQualityGateConfig(path);
      config =
          discovered.map(QualityGateConfig::fromFile).orElseGet(QualityGateConfig::fromDefaults);
    }

    MFCQICalculator calc = MFCQIDefaults.calculatorFor(path);
    Map<String, Double> detailed = calc.detailedMetrics(path);
    double overall = detailed.getOrDefault("mfcqi_score", 0.0);
    Map<String, Double> metricScores = new LinkedHashMap<>(detailed);
    metricScores.remove("mfcqi_score");

    Map<String, Object> analysisResult = new LinkedHashMap<>();
    analysisResult.put("mfcqi_score", overall);
    analysisResult.put("metric_scores", metricScores);

    QualityGateResult r = new QualityGateEvaluator(config).evaluate(analysisResult);

    System.out.println("MFCQI score: " + String.format(Locale.ROOT, "%.3f", overall));
    System.out.println("Overall gate: " + (r.overallResult() ? "PASS" : "FAIL"));
    for (QualityGateResult.MetricResult mr : r.metricResults()) {
      System.out.println(
          "  "
              + (mr.passed() ? "PASS" : "FAIL")
              + " "
              + mr.metric()
              + ": "
              + String.format(Locale.ROOT, "%.3f", mr.actual())
              + " (threshold "
              + String.format(Locale.ROOT, "%.3f", mr.threshold())
              + ")");
    }
    System.out.println(
        "Result: " + (r.passed() ? "ALL GATES PASS" : r.failedCount() + " gate(s) failed"));
    return r.passed() ? 0 : 1;
  }
}
