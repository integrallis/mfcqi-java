package com.integrallis.mfcqi.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.integrallis.mfcqi.analysis.AnalysisConfig;
import com.integrallis.mfcqi.analysis.AnalysisEngine;
import com.integrallis.mfcqi.analysis.AnalysisResult;
import com.integrallis.mfcqi.analysis.AnthropicProvider;
import com.integrallis.mfcqi.analysis.LLMProvider;
import com.integrallis.mfcqi.analysis.OllamaProvider;
import com.integrallis.mfcqi.analysis.OpenAIProvider;
import com.integrallis.mfcqi.analysis.ToolOutputs;
import com.integrallis.mfcqi.cli.MFCQIDefaults;
import com.integrallis.mfcqi.cli.Spinner;
import com.integrallis.mfcqi.cli.ToolOutputCollector;
import com.integrallis.mfcqi.core.MFCQICalculator;
import com.integrallis.mfcqi.qualitygates.QualityGateConfig;
import com.integrallis.mfcqi.qualitygates.QualityGateEvaluator;
import com.integrallis.mfcqi.qualitygates.QualityGateResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code mfcqi analyze} — runs the default {@link MFCQICalculator} against a codebase, optionally
 * passes the metric breakdown to the LLM analysis engine, and renders the result. Mirrors {@code
 * mfcqi/cli/commands/analyze.py}, including:
 *
 * <ul>
 *   <li>LLM is opt-in: skipped unless {@code --model} or {@code --provider} is explicitly set.
 *   <li>{@code --skip-llm} / {@code --metrics-only} forces metrics-only.
 *   <li>{@code json} / {@code sarif} output formats auto-enable {@code --silent}.
 *   <li>{@code --quality-gate} runs gate evaluation after rendering, exiting non-zero on failure.
 * </ul>
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
      description = "Output format: terminal, json, html, markdown, sarif. Default: terminal.")
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
      description = "Override LLM model (e.g., claude-sonnet-4-5, gpt-4o, ollama:llama3).")
  String model;

  @Option(
      names = {"--provider"},
      description = "Force LLM provider. Valid values: ${COMPLETION-CANDIDATES}.")
  ProviderName provider;

  /**
   * Closed-set provider choice — Java analog of Python's {@code click.Choice(["anthropic",
   * "openai", "ollama"])}. Picocli rejects values outside this enum at parse time, mirroring
   * Click's UsageError.
   */
  public enum ProviderName {
    /** Anthropic Claude models. */
    anthropic,
    /** OpenAI GPT models. */
    openai,
    /** Locally-hosted Ollama models. */
    ollama
  }

  @Option(
      names = {"--ollama-endpoint"},
      description = "Ollama HTTP endpoint. Default: http://localhost:11434.")
  String ollamaEndpoint = OllamaProvider.DEFAULT_ENDPOINT;

  @Option(
      names = {"--recommendations"},
      description = "Number of recommendations to request from the LLM (default 50).")
  int recommendationCount = 50;

  @Option(
      names = {"--timeout"},
      description =
          "LLM request timeout in seconds (default 60; also via MFCQI_TIMEOUT). "
              + "Raise it for slow local models.")
  int timeoutSeconds = 0; // 0 = fall back to the env/config default

  @Option(
      names = {"--min-score"},
      description = "Exit with code 1 if MFCQI score is below this threshold.")
  Double minScore;

  @Option(
      names = {"--quality-gate"},
      description = "Evaluate against .mfcqi.yaml quality gates after rendering output.")
  boolean qualityGate;

  @Option(
      names = {"--silent"},
      description = "Suppress non-essential stderr (auto-enabled for json/sarif output).")
  boolean silent;

  @Override
  public Integer call() {
    if (!Files.isDirectory(path) && !Files.isRegularFile(path)) {
      System.err.println("Path does not exist or is not analyzable: " + path);
      return 2;
    }

    // Auto-silent for machine formats — Python: `if output_format in ("json", "sarif"): silent =
    // True`
    String fmt = format == null ? "terminal" : format.toLowerCase(Locale.ROOT);
    if ("json".equals(fmt) || "sarif".equals(fmt)) {
      silent = true;
    }

    // LLM is opt-in — Python: `should_skip_llm = (skip_llm or metrics_only) or not
    // explicitly_requested_llm`
    boolean explicitlyRequestedLlm = (model != null && !model.isEmpty()) || provider != null;
    boolean shouldSkipLlm = skipLlm || !explicitlyRequestedLlm;

    MFCQICalculator calculator = MFCQIDefaults.calculator();
    Map<String, Double> detailed = calculator.detailedMetrics(path);
    double score = detailed.getOrDefault("mfcqi_score", 0.0);

    AnalysisResult llmResult = null;
    if (!shouldSkipLlm) {
      llmResult = runLlm(detailed);
    } else if (!silent) {
      System.err.println("Analysis complete (metrics-only mode)");
    }

    String rendered = render(fmt, path, score, detailed, llmResult);
    writeOutput(rendered);

    if (minScore != null && score < minScore) {
      return 1;
    }

    if (qualityGate) {
      return runQualityGate(score, detailed) ? 0 : 1;
    }
    return 0;
  }

  AnalysisResult runLlm(Map<String, Double> metrics) {
    AnalysisConfig.Builder b = AnalysisConfig.builder();
    // Use dotenv fallback so developer-local .env files supply API keys without exporting them.
    AnalysisConfig fromEnv = AnalysisConfig.fromEnvironmentAndDotenv(path);
    fromEnv.anthropicApiKey().ifPresent(b::anthropicApiKey);
    fromEnv.openaiApiKey().ifPresent(b::openaiApiKey);
    if (model != null && !model.isEmpty()) {
      b.model(normalizeModel(model, provider));
    } else if (provider != null) {
      b.model(defaultModelForProvider(provider));
    } else {
      b.model(fromEnv.model());
    }
    // CLI --timeout wins; otherwise use the env/config value (MFCQI_TIMEOUT or the 60s default).
    b.timeoutSeconds(timeoutSeconds > 0 ? timeoutSeconds : fromEnv.timeoutSeconds());
    AnalysisConfig cfg = b.build();
    try {
      cfg.validate();
    } catch (RuntimeException e) {
      if (!silent) {
        System.err.println("LLM analysis skipped: " + e.getMessage());
      }
      return null;
    }
    AnalysisEngine engine = analysisEngineFor(cfg);
    Spinner spinner =
        silent
            ? null
            : Spinner.start(
                "Querying " + cfg.model() + " for recommendations (may take a while)...");
    try {
      ToolOutputs toolOutputs = ToolOutputCollector.collect(path, metrics);
      return engine.analyze(path.toString(), metrics, toolOutputs, recommendationCount, cfg);
    } catch (RuntimeException e) {
      if (!silent) {
        System.err.println("LLM analysis failed: " + e.getMessage());
      }
      return null;
    } finally {
      if (spinner != null) {
        spinner.stop();
      }
    }
  }

  /**
   * Ensures the model string routes to the explicitly-chosen provider. For {@code --provider
   * ollama} the model needs an {@code ollama:} prefix (that is how the Ollama provider claims the
   * model and how validation knows no API key is required); without this, {@code --provider ollama
   * --model qwen3-coder} would be mis-routed to a key-based provider and rejected.
   *
   * @param model the requested model id
   * @param provider the forced provider, or {@code null}
   * @return the model id, prefixed for Ollama when needed
   */
  static String normalizeModel(String model, ProviderName provider) {
    if (provider == ProviderName.ollama
        && !model.startsWith("ollama:")
        && !model.startsWith("ollama/")) {
      return "ollama:" + model;
    }
    return model;
  }

  private AnalysisEngine analysisEngineFor(AnalysisConfig cfg) {
    // Honour --ollama-endpoint by injecting a fresh OllamaProvider with the chosen endpoint.
    List<LLMProvider> providers =
        Arrays.asList(
            new AnthropicProvider(), new OpenAIProvider(), new OllamaProvider(ollamaEndpoint));
    return new AnalysisEngine(providers);
  }

  static String defaultModelForProvider(ProviderName provider) {
    switch (provider) {
      case openai:
        return "gpt-4o";
      case ollama:
        return "ollama:llama3";
      case anthropic:
      default:
        return AnalysisConfig.DEFAULT_MODEL;
    }
  }

  boolean runQualityGate(double score, Map<String, Double> detailed) {
    Optional<Path> discovered = QualityGateEvaluator.findQualityGateConfig(path);
    QualityGateConfig config =
        discovered.map(QualityGateConfig::fromFile).orElseGet(QualityGateConfig::fromDefaults);
    Map<String, Double> metricScores = new LinkedHashMap<>(detailed);
    metricScores.remove("mfcqi_score");
    Map<String, Object> analysisResult = new LinkedHashMap<>();
    analysisResult.put("mfcqi_score", score);
    analysisResult.put("metric_scores", metricScores);
    QualityGateResult r = new QualityGateEvaluator(config).evaluate(analysisResult);
    if (!silent) {
      System.err.println("Quality gates: " + (r.passed() ? "PASS" : r.failedCount() + " failed"));
      for (QualityGateResult.MetricResult mr : r.metricResults()) {
        System.err.println(
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
    }
    return r.passed();
  }

  String render(
      String fmt,
      Path codebasePath,
      double score,
      Map<String, Double> metrics,
      AnalysisResult llm) {
    switch (fmt) {
      case "json":
        return renderJson(codebasePath, score, metrics, llm);
      case "html":
        return renderHtml(codebasePath, score, metrics, llm);
      case "markdown":
        return renderMarkdown(codebasePath, score, metrics, llm);
      case "sarif":
        return renderSarif(codebasePath, score, metrics, llm);
      case "terminal":
      default:
        return renderTerminal(codebasePath, score, metrics, llm);
    }
  }

  /**
   * HTML report — direct port of {@code mfcqi/cli/utils/output.py:_format_html_output}. Inline
   * styles match the Python source (color thresholds 0.8/0.6, score emoji ladder 0.9/0.8/0.7/0.6).
   */
  static String renderHtml(
      Path codebasePath, double score, Map<String, Double> metrics, AnalysisResult llm) {
    StringBuilder sb = new StringBuilder();
    sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
    sb.append("    <title>MFCQI Analysis Report</title>\n");
    sb.append("    <style>\n");
    sb.append("        body { font-family: Arial, sans-serif; margin: 40px; }\n");
    sb.append("        .score { font-size: 24px; font-weight: bold; color: ")
        .append(scoreColorHex(score))
        .append("; }\n");
    sb.append("        .metric { margin: 10px 0; }\n");
    sb.append("        .recommendations { margin-top: 20px; }\n");
    sb.append(
        "        .recommendation { margin: 8px 0; padding: 8px; background: #f5f5f5; border-radius: 4px; }\n");
    sb.append("    </style>\n</head>\n<body>\n");
    sb.append("    <h1>MFCQI Analysis Report</h1>\n");
    sb.append("    <div class=\"score\">Codebase: ")
        .append(escapeHtml(codebasePath.toString()))
        .append("</div>\n");
    sb.append("    <div class=\"score\">Overall Score: ")
        .append(String.format(Locale.ROOT, "%.2f", score))
        .append("/1.0 ")
        .append(scoreEmoji(score))
        .append("</div>\n");
    sb.append("    <h2>Metrics</h2>\n");
    for (Map.Entry<String, Double> e : metrics.entrySet()) {
      if ("mfcqi_score".equals(e.getKey())) {
        continue;
      }
      sb.append("    <div class=\"metric\"><strong>")
          .append(escapeHtml(e.getKey()))
          .append(":</strong> ")
          .append(String.format(Locale.ROOT, "%.2f", e.getValue()))
          .append("</div>\n");
    }
    if (llm != null && !llm.recommendations().isEmpty()) {
      sb.append("    <h2>AI Recommendations</h2>\n    <div class=\"recommendations\">\n");
      int i = 1;
      for (String rec : llm.recommendations()) {
        sb.append("        <div class=\"recommendation\">")
            .append(i)
            .append(". ")
            .append(escapeHtml(rec))
            .append("</div>\n");
        i++;
      }
      sb.append("    </div>\n");
    }
    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  /** Verbatim port of Python's {@code _get_score_color_hex}. */
  static String scoreColorHex(double score) {
    if (score >= 0.8) {
      return "#00aa00";
    }
    if (score >= 0.6) {
      return "#ffaa00";
    }
    return "#aa0000";
  }

  /** Verbatim port of Python's {@code _get_score_emoji} (kept ASCII-only labels). */
  static String scoreEmoji(double score) {
    if (score >= 0.9) {
      return "[trophy]";
    }
    if (score >= 0.8) {
      return "[star]";
    }
    if (score >= 0.7) {
      return "[ok]";
    }
    if (score >= 0.6) {
      return "[warn]";
    }
    return "[fail]";
  }

  private static String escapeHtml(String s) {
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
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

  /**
   * SARIF 2.1.0 envelope — direct port of {@code mfcqi/cli/utils/output.py:_format_as_sarif}.
   * Driver metadata + a rule per metric (with description and helpUri) + one result per metric +
   * one result per overall score + each LLM recommendation as a {@code note}-level result.
   */
  static String renderSarif(
      Path codebasePath, double score, Map<String, Double> metrics, AnalysisResult llm) {
    Map<String, Double> ms = new LinkedHashMap<>(metrics);
    ms.remove("mfcqi_score");

    Map<String, Object> driver = new LinkedHashMap<>();
    driver.put("name", "MFCQI");
    driver.put("version", "0.1.0-SNAPSHOT");
    driver.put("informationUri", "https://github.com/bsbodden/mfcqi");
    driver.put("semanticVersion", "0.1.0-SNAPSHOT");
    driver.put("rules", buildSarifRules(ms));

    Map<String, Object> tool = new LinkedHashMap<>();
    tool.put("driver", driver);

    java.util.List<String> recommendations =
        llm == null ? java.util.Collections.emptyList() : llm.recommendations();
    Map<String, Object> run = new LinkedHashMap<>();
    run.put("tool", tool);
    run.put("results", buildSarifResults(score, ms, recommendations));

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("version", "2.1.0");
    root.put(
        "$schema",
        "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json");
    root.put("runs", java.util.Collections.singletonList(run));
    try {
      return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root);
    } catch (Exception e) {
      return "{}";
    }
  }

  static java.util.List<Map<String, Object>> buildSarifRules(Map<String, Double> metrics) {
    java.util.List<Map<String, Object>> rules = new java.util.ArrayList<>();
    for (String metricName : metrics.keySet()) {
      MetricRuleDescription d =
          METRIC_RULE_DESCRIPTIONS.getOrDefault(metricName, defaultDescription(metricName));
      rules.add(rule(metricName, d));
    }
    rules.add(
        rule(
            "mfcqi_score",
            new MetricRuleDescription(
                "MFCQI Overall Score",
                "Multi-Factor Code Quality Index",
                "Composite code quality score combining multiple evidence-based metrics using geometric mean.")));
    return rules;
  }

  private static Map<String, Object> rule(String id, MetricRuleDescription d) {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("id", id);
    rule.put("name", d.name);
    Map<String, Object> sd = new LinkedHashMap<>();
    sd.put("text", d.shortDescription);
    rule.put("shortDescription", sd);
    Map<String, Object> fd = new LinkedHashMap<>();
    fd.put("text", d.fullDescription);
    rule.put("fullDescription", fd);
    rule.put("helpUri", "https://github.com/bsbodden/mfcqi");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put(
        "tags",
        java.util.Arrays.asList(
            "code-quality", "mfcqi_score".equals(id) ? "composite" : "metrics"));
    rule.put("properties", props);
    return rule;
  }

  static java.util.List<Map<String, Object>> buildSarifResults(
      double overallScore, Map<String, Double> metrics, java.util.List<String> recommendations) {
    java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();

    Map<String, Object> overall = new LinkedHashMap<>();
    overall.put("ruleId", "mfcqi_score");
    overall.put("level", scoreToSarifLevel(overallScore));
    Map<String, Object> overallMsg = new LinkedHashMap<>();
    overallMsg.put(
        "text",
        "Overall code quality score: "
            + String.format(Locale.ROOT, "%.3f", overallScore)
            + "/1.0 ("
            + scoreToRating(overallScore)
            + ")");
    overall.put("message", overallMsg);
    Map<String, Object> overallProps = new LinkedHashMap<>();
    overallProps.put("score", overallScore);
    overall.put("properties", overallProps);
    results.add(overall);

    for (Map.Entry<String, Double> e : metrics.entrySet()) {
      Map<String, Object> r = new LinkedHashMap<>();
      r.put("ruleId", e.getKey());
      r.put("level", scoreToSarifLevel(e.getValue()));
      Map<String, Object> msg = new LinkedHashMap<>();
      msg.put(
          "text",
          e.getKey()
              + ": "
              + String.format(Locale.ROOT, "%.2f", e.getValue())
              + " ("
              + scoreToRating(e.getValue())
              + ")");
      r.put("message", msg);
      Map<String, Object> p = new LinkedHashMap<>();
      p.put("score", e.getValue());
      r.put("properties", p);
      results.add(r);
    }

    int i = 1;
    for (String rec : recommendations) {
      Map<String, Object> r = new LinkedHashMap<>();
      r.put("ruleId", "mfcqi_score");
      r.put("level", "note");
      Map<String, Object> msg = new LinkedHashMap<>();
      msg.put("text", "Recommendation " + i + ": " + rec.trim());
      r.put("message", msg);
      Map<String, Object> p = new LinkedHashMap<>();
      p.put("type", "recommendation");
      r.put("properties", p);
      results.add(r);
      i++;
    }
    return results;
  }

  /** Verbatim port of Python's {@code _score_to_sarif_level}. */
  static String scoreToSarifLevel(double score) {
    if (score >= 0.8) {
      return "none";
    }
    if (score >= 0.6) {
      return "note";
    }
    if (score >= 0.4) {
      return "warning";
    }
    return "error";
  }

  /** Verbatim port of Python's {@code _score_to_rating}. */
  static String scoreToRating(double score) {
    if (score >= 0.8) {
      return "Excellent";
    }
    if (score >= 0.6) {
      return "Good";
    }
    if (score >= 0.4) {
      return "Needs Work";
    }
    return "Poor";
  }

  private static MetricRuleDescription defaultDescription(String metricName) {
    return new MetricRuleDescription(
        metricName, "Measures " + metricName, "Code quality metric: " + metricName);
  }

  static final class MetricRuleDescription {
    final String name;
    final String shortDescription;
    final String fullDescription;

    MetricRuleDescription(String name, String shortDescription, String fullDescription) {
      this.name = name;
      this.shortDescription = shortDescription;
      this.fullDescription = fullDescription;
    }
  }

  /** Per-metric rule descriptions — Java metric names map to the same shape Python uses. */
  static final Map<String, MetricRuleDescription> METRIC_RULE_DESCRIPTIONS;

  static {
    Map<String, MetricRuleDescription> m = new LinkedHashMap<>();
    m.put(
        "Cyclomatic Complexity",
        new MetricRuleDescription(
            "Cyclomatic Complexity",
            "Measures code complexity based on decision points",
            "Cyclomatic complexity measures the number of linearly independent paths through code. Lower is better."));
    m.put(
        "Cognitive Complexity",
        new MetricRuleDescription(
            "Cognitive Complexity",
            "Measures code understandability",
            "Cognitive complexity measures how difficult code is to understand, focusing on nested structures and control flow."));
    m.put(
        "Maintainability Index",
        new MetricRuleDescription(
            "Maintainability Index",
            "Composite maintainability metric",
            "Maintainability Index combines Halstead Volume, Cyclomatic Complexity, and lines of code into a single metric."));
    m.put(
        "Halstead Volume",
        new MetricRuleDescription(
            "Halstead Volume",
            "Measures program vocabulary and length",
            "Halstead Volume measures the size of the implementation based on the number of operations and operands."));
    m.put(
        "Code Duplication",
        new MetricRuleDescription(
            "Code Duplication",
            "Detects duplicate code blocks",
            "Code duplication measures the percentage of code that is duplicated across the codebase."));
    m.put(
        "Documentation Coverage",
        new MetricRuleDescription(
            "Documentation Coverage",
            "Measures documentation completeness",
            "Documentation coverage measures the percentage of code that has proper documentation."));
    m.put(
        "security",
        new MetricRuleDescription(
            "Security Score",
            "Security vulnerability assessment",
            "Security score based on static analysis findings (Bandit-equivalent), secrets detection, and dependency scanning."));
    m.put(
        "Dependency Security",
        new MetricRuleDescription(
            "Dependency Security",
            "Dependency vulnerability assessment",
            "Dependency security score based on CVE scanning of declared Maven and Gradle dependencies."));
    m.put(
        "Secrets Exposure",
        new MetricRuleDescription(
            "Secrets Exposure",
            "Hard-coded credential detection",
            "Secrets exposure score based on regex catalog and Shannon-entropy detection of hard-coded credentials."));
    m.put(
        "Code Smell Density",
        new MetricRuleDescription(
            "Code Smell Density",
            "Weighted smell count per KLOC",
            "Aggregates production and test smell detectors into a category-weighted density per 1K lines of code."));
    METRIC_RULE_DESCRIPTIONS = java.util.Collections.unmodifiableMap(m);
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
