package com.integrallis.mfcqi.analysis;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates an LLM call: builds a Pebble-rendered prompt from pre-calculated MFCQI metrics,
 * dispatches to the matching {@link LLMProvider}, and parses the response into recommendation
 * strings of the form {@code "[SEVERITY] Title: Description"}.
 *
 * <p>Direct port of {@code mfcqi/analysis/engine.py:LLMAnalysisEngine.analyze_with_cqi_data}. The
 * Pebble template at {@code templates/code_quality_analysis.peb} is a Pebble adaptation of the
 * Python {@code code_quality_analysis.j2}, with filter changes documented in the template.
 */
public final class AnalysisEngine {

  private static final Pattern HEADING =
      Pattern.compile("^##\\s*\\[(\\w+)\\]\\s*(.+)$", Pattern.MULTILINE);
  private static final Pattern DESCRIPTION =
      Pattern.compile("\\*\\*Description:\\*\\*\\s*(.+?)(?=\\*\\*|$)", Pattern.DOTALL);

  private final List<LLMProvider> providers;
  private final PebbleEngine pebble;

  public AnalysisEngine(List<LLMProvider> providers) {
    this.providers = new ArrayList<>(Objects.requireNonNull(providers, "providers"));
    ClasspathLoader loader = new ClasspathLoader();
    loader.setPrefix("templates/");
    this.pebble =
        new PebbleEngine.Builder()
            .loader(loader)
            .autoEscaping(false)
            .strictVariables(false)
            .build();
  }

  /** Default wiring: Anthropic + OpenAI + Ollama. */
  public static AnalysisEngine withDefaults() {
    return new AnalysisEngine(
        java.util.Arrays.asList(
            new AnthropicProvider(), new OpenAIProvider(), new OllamaProvider()));
  }

  /**
   * Analyze pre-calculated metrics. {@code metrics} must contain at least an {@code "mfcqi_score"}
   * entry; the remaining entries are treated as per-metric scores. {@code recommendationCount} is
   * the number of recommendations to request from the LLM.
   */
  public AnalysisResult analyze(
      String codebasePath,
      Map<String, Double> metrics,
      int recommendationCount,
      AnalysisConfig config) {
    return analyze(codebasePath, metrics, ToolOutputs.empty(), recommendationCount, config);
  }

  /**
   * Analyze pre-calculated metrics with raw tool-output context. The prompt template uses {@code
   * tool_outputs.bandit.*} and {@code tool_outputs.complexity.*} sections (mirroring Python's
   * {@code mfcqi/analysis/engine.py:_format_tool_outputs}), so populating {@code toolOutputs} gives
   * the LLM concrete file:line evidence to ground each recommendation.
   */
  public AnalysisResult analyze(
      String codebasePath,
      Map<String, Double> metrics,
      ToolOutputs toolOutputs,
      int recommendationCount,
      AnalysisConfig config) {
    config.validate();
    LLMProvider provider = pickProvider(config.model());
    String prompt = renderPrompt(codebasePath, metrics, toolOutputs, recommendationCount);
    String response = provider.complete(prompt, config);
    List<String> recs = parseRecommendations(response, recommendationCount);
    Map<String, Double> metricScores = new LinkedHashMap<>(metrics);
    Double overall = metricScores.remove("mfcqi_score");
    return new AnalysisResult(overall == null ? 0.0 : overall, metricScores, recs, config.model());
  }

  LLMProvider pickProvider(String modelName) {
    for (LLMProvider p : providers) {
      if (p.handles(modelName)) {
        return p;
      }
    }
    throw new LLMException("No provider registered for model " + modelName);
  }

  String renderPrompt(String codebasePath, Map<String, Double> metrics, int recommendationCount) {
    return renderPrompt(codebasePath, metrics, ToolOutputs.empty(), recommendationCount);
  }

  String renderPrompt(
      String codebasePath,
      Map<String, Double> metrics,
      ToolOutputs toolOutputs,
      int recommendationCount) {
    Map<String, Object> ctx = new LinkedHashMap<>();
    ctx.put("codebase_path", codebasePath);
    ctx.put("recommendation_count", recommendationCount);
    Map<String, Double> metricScores = new LinkedHashMap<>(metrics);
    Double overall = metricScores.remove("mfcqi_score");
    ctx.put("mfcqi_score", overall == null ? Double.valueOf(0.0) : overall);
    ctx.put("metrics", metricScores);
    ctx.put("tool_outputs", toolOutputs);
    ctx.put("total_files", toolOutputs.getTotalFiles());
    ctx.put("total_lines", toolOutputs.getTotalLines());

    List<Map<String, Object>> critical = new ArrayList<>();
    for (Map.Entry<String, Double> e : metricScores.entrySet()) {
      if (e.getValue() < 0.3) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", e.getKey());
        entry.put("score", e.getValue());
        critical.add(entry);
      }
    }
    ctx.put("critical_metrics", critical);

    try (StringWriter out = new StringWriter()) {
      PebbleTemplate template = pebble.getTemplate("code_quality_analysis.peb");
      template.evaluate(out, ctx);
      return out.toString();
    } catch (IOException e) {
      throw new LLMException("Failed to render prompt template", e);
    }
  }

  /** Verbatim port of Python's _parse_recommendations regex-based extraction. */
  static List<String> parseRecommendations(String response, int max) {
    List<String> out = new ArrayList<>();
    if (response == null || response.isEmpty()) {
      return out;
    }
    // Python: `re.split(r"\n(?=##\s*\[)", response)`. Java: positive lookahead split that
    // anchors on every "## [" heading except the first.
    String[] sections = response.split("(?=^##\\s*\\[|\\n##\\s*\\[)");
    for (String rawSection : sections) {
      String section = rawSection.trim();
      if (section.isEmpty() || !section.startsWith("##")) {
        continue;
      }
      Matcher heading = HEADING.matcher(section);
      if (!heading.find()) {
        continue;
      }
      String severity = heading.group(1).toUpperCase(java.util.Locale.ROOT);
      String title = heading.group(2).trim();
      Matcher desc = DESCRIPTION.matcher(section);
      String description = desc.find() ? desc.group(1).trim() : "";
      if (title.isEmpty() || description.isEmpty()) {
        continue;
      }
      // Truncate description to first sentence or 200 chars (Python verbatim).
      int dot = description.indexOf(". ");
      if (dot >= 0) {
        description = description.substring(0, dot + 1);
      } else if (description.length() > 200) {
        description = description.substring(0, 200) + "...";
      }
      out.add("[" + severity + "] " + title + ": " + description);
      if (out.size() >= max) {
        break;
      }
    }
    return out;
  }
}
