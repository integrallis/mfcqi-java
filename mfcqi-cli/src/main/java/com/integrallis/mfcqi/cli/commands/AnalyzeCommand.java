package com.integrallis.mfcqi.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.integrallis.mfcqi.analysis.AnalysisConfig;
import com.integrallis.mfcqi.analysis.AnalysisEngine;
import com.integrallis.mfcqi.analysis.AnalysisResult;
import com.integrallis.mfcqi.cli.MFCQIDefaults;
import com.integrallis.mfcqi.core.MFCQICalculator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code mfcqi analyze} — runs the default {@link MFCQICalculator} against a codebase, optionally
 * passes the metric breakdown to the LLM analysis engine, and renders the result in one of four
 * formats (terminal, json, markdown, sarif). Mirrors {@code mfcqi/cli/commands/analyze.py}.
 */
@Command(
    name = "analyze",
    mixinStandardHelpOptions = true,
    description = "Analyze a Java codebase and produce an MFCQI score.")
public final class AnalyzeCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Path to the codebase directory.", arity = "0..1")
  Path path = Path.of(".");

  @Option(
      names = {"--format", "-f"},
      description = "Output format: terminal, json, markdown, sarif. Default: terminal.")
  String format = "terminal";

  @Option(
      names = {"--output", "-o"},
      description = "Write output to FILE instead of stdout.")
  Path output;

  @Option(
      names = {"--skip-llm", "--metrics-only"},
      description = "Skip the LLM recommendation step.")
  boolean skipLlm;

  @Option(
      names = {"--model"},
      description = "Override LLM model (e.g., claude-3-5-sonnet-20241022, gpt-4o, ollama:llama3).")
  String model;

  @Option(
      names = {"--recommendations"},
      description = "Number of recommendations to request from the LLM (default 50).")
  int recommendationCount = 50;

  @Option(
      names = {"--min-score"},
      description = "Exit with code 1 if MFCQI score is below this threshold.")
  Double minScore;

  @Override
  public Integer call() {
    if (!Files.isDirectory(path) && !Files.isRegularFile(path)) {
      System.err.println("Path does not exist or is not analyzable: " + path);
      return 2;
    }

    MFCQICalculator calculator = MFCQIDefaults.calculator();
    Map<String, Double> detailed = calculator.detailedMetrics(path);
    double score = detailed.getOrDefault("mfcqi_score", 0.0);

    AnalysisResult llmResult = null;
    if (!skipLlm) {
      llmResult = tryRunLlm(detailed);
    }

    String rendered = render(format, path, score, detailed, llmResult);
    writeOutput(rendered);

    if (minScore != null && score < minScore) {
      return 1;
    }
    return 0;
  }

  AnalysisResult tryRunLlm(Map<String, Double> metrics) {
    AnalysisConfig.Builder b = AnalysisConfig.builder();
    if (model != null && !model.isEmpty()) {
      b.model(model);
    }
    AnalysisConfig fromEnv = AnalysisConfig.fromEnvironment();
    fromEnv.anthropicApiKey().ifPresent(b::anthropicApiKey);
    fromEnv.openaiApiKey().ifPresent(b::openaiApiKey);
    if (model == null || model.isEmpty()) {
      b.model(fromEnv.model());
    }
    AnalysisConfig cfg = b.build();
    try {
      cfg.validate();
    } catch (RuntimeException e) {
      // No API key configured — fall back to metrics-only quietly. Python prints a warning;
      // we mirror that.
      System.err.println("LLM analysis skipped: " + e.getMessage());
      return null;
    }
    try {
      return AnalysisEngine.withDefaults()
          .analyze(path.toString(), metrics, recommendationCount, cfg);
    } catch (RuntimeException e) {
      System.err.println("LLM analysis failed: " + e.getMessage());
      return null;
    }
  }

  String render(
      String fmt,
      Path codebasePath,
      double score,
      Map<String, Double> metrics,
      AnalysisResult llm) {
    switch (fmt.toLowerCase(Locale.ROOT)) {
      case "json":
        return renderJson(codebasePath, score, metrics, llm);
      case "markdown":
        return renderMarkdown(codebasePath, score, metrics, llm);
      case "sarif":
        return renderSarif(codebasePath, score, metrics, llm);
      case "terminal":
      default:
        return renderTerminal(codebasePath, score, metrics, llm);
    }
  }

  static String renderTerminal(
      Path codebasePath, double score, Map<String, Double> metrics, AnalysisResult llm) {
    StringBuilder sb = new StringBuilder();
    sb.append("MFCQI analysis: ").append(codebasePath).append('\n');
    sb.append("Score: ").append(String.format(Locale.ROOT, "%.3f", score)).append('\n');
    sb.append('\n').append("Metric breakdown:").append('\n');
    for (Map.Entry<String, Double> e : metrics.entrySet()) {
      if ("mfcqi_score".equals(e.getKey())) {
        continue;
      }
      sb.append("  ")
          .append(String.format(Locale.ROOT, "%-30s", e.getKey()))
          .append(' ')
          .append(String.format(Locale.ROOT, "%.3f", e.getValue()))
          .append('\n');
    }
    if (llm != null && !llm.recommendations().isEmpty()) {
      sb.append('\n').append("Recommendations:").append('\n');
      List<String> recs = llm.recommendations();
      for (int i = 0; i < recs.size(); i++) {
        sb.append("  ").append(i + 1).append(". ").append(recs.get(i)).append('\n');
      }
    }
    return sb.toString();
  }

  static String renderMarkdown(
      Path codebasePath, double score, Map<String, Double> metrics, AnalysisResult llm) {
    StringBuilder sb = new StringBuilder();
    sb.append("# MFCQI Report\n\n");
    sb.append("**Codebase:** `").append(codebasePath).append("`  \n");
    sb.append("**Score:** ").append(String.format(Locale.ROOT, "%.3f", score)).append("\n\n");
    sb.append("| Metric | Score |\n|---|---|\n");
    for (Map.Entry<String, Double> e : metrics.entrySet()) {
      if ("mfcqi_score".equals(e.getKey())) {
        continue;
      }
      sb.append("| ")
          .append(e.getKey())
          .append(" | ")
          .append(String.format(Locale.ROOT, "%.3f", e.getValue()))
          .append(" |\n");
    }
    if (llm != null && !llm.recommendations().isEmpty()) {
      sb.append("\n## Recommendations\n\n");
      for (String r : llm.recommendations()) {
        sb.append("- ").append(r).append('\n');
      }
    }
    return sb.toString();
  }

  static String renderJson(
      Path codebasePath, double score, Map<String, Double> metrics, AnalysisResult llm) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("codebase", codebasePath.toString());
    root.put("mfcqi_score", score);
    Map<String, Double> ms = new LinkedHashMap<>(metrics);
    ms.remove("mfcqi_score");
    root.put("metric_scores", ms);
    if (llm != null) {
      root.put("recommendations", llm.recommendations());
      root.put("model", llm.modelUsed());
    }
    try {
      return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root);
    } catch (Exception e) {
      return "{}";
    }
  }

  /** Minimal SARIF 2.1.0 envelope with one result per below-threshold metric. */
  static String renderSarif(
      Path codebasePath, double score, Map<String, Double> metrics, AnalysisResult llm) {
    Map<String, Object> tool = new LinkedHashMap<>();
    Map<String, Object> driver = new LinkedHashMap<>();
    driver.put("name", "mfcqi-java");
    driver.put("version", "0.1.0-SNAPSHOT");
    tool.put("driver", driver);

    java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
    for (Map.Entry<String, Double> e : metrics.entrySet()) {
      if ("mfcqi_score".equals(e.getKey()) || e.getValue() >= 0.6) {
        continue;
      }
      Map<String, Object> r = new LinkedHashMap<>();
      r.put("ruleId", "mfcqi/" + e.getKey().toLowerCase(Locale.ROOT).replace(' ', '-'));
      Map<String, Object> message = new LinkedHashMap<>();
      message.put(
          "text",
          e.getKey()
              + " score "
              + String.format(Locale.ROOT, "%.3f", e.getValue())
              + " is below the warning threshold (0.60).");
      r.put("message", message);
      r.put("level", e.getValue() < 0.3 ? "error" : "warning");
      results.add(r);
    }

    Map<String, Object> run = new LinkedHashMap<>();
    run.put("tool", tool);
    run.put("results", results);
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
    root.put("version", "2.1.0");
    root.put("runs", java.util.Collections.singletonList(run));
    try {
      return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root);
    } catch (Exception e) {
      return "{}";
    }
  }

  void writeOutput(String content) {
    if (output == null) {
      System.out.print(content);
      return;
    }
    try {
      Files.write(output, content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      System.err.println("Failed to write " + output + ": " + e.getMessage());
    }
  }
}
