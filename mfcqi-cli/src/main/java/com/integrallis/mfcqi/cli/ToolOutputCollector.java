package com.integrallis.mfcqi.cli;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.integrallis.mfcqi.analysis.ToolOutputs;
import com.integrallis.mfcqi.core.JavaSourceFiles;
import com.integrallis.mfcqi.security.JavaSecurityScanner;
import com.integrallis.mfcqi.security.SecurityFinding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Gathers raw tool outputs (security findings, complexity hotspots, repo size) for an LLM analysis.
 * Java analog of the {@code calculate_metrics(..., need_tool_outputs=True)} pathway in {@code
 * mfcqi/cli/commands/analyze.py} — the data is fed to {@link
 * com.integrallis.mfcqi.analysis.AnalysisEngine#analyze} so the prompt template can ground each
 * recommendation in concrete evidence.
 *
 * <p>This collector intentionally re-runs the underlying scanners rather than reaching into
 * already-computed metric internals: it keeps the metric API surface narrow and lets the CLI decide
 * when the extra cost of detailed extraction is worthwhile (only when LLM analysis is requested).
 */
public final class ToolOutputCollector {

  private static final int COMPLEXITY_THRESHOLD = 10;
  private static final int MAX_HOTSPOTS = 10;

  private ToolOutputCollector() {}

  /** Collect tool outputs for {@code codebase}. Pre-computed metric scores inform raw values. */
  public static ToolOutputs collect(Path codebase, Map<String, Double> normalizedScores) {
    ToolOutputs.Builder b = ToolOutputs.builder();

    List<Path> files = JavaSourceFiles.findAll(codebase);
    b.totalFiles(files.size());
    b.totalLines(countSourceLines(files));

    b.bandit(collectSecurityIssues(codebase));
    b.complexity(collectComplexityHotspots(files));

    Double cc = normalizedScores.get("Cyclomatic Complexity");
    if (cc != null) {
      // We only have the normalized value here; the raw section in the prompt uses 0 to mean
      // "not available". A future enhancement would expose raw averages from the metric.
      b.cyclomaticComplexityRaw(0.0);
    }
    Double hv = normalizedScores.get("Halstead Volume");
    if (hv != null) {
      b.halsteadVolumeRaw(0.0);
    }

    return b.build();
  }

  static List<ToolOutputs.SecurityIssue> collectSecurityIssues(Path codebase) {
    List<ToolOutputs.SecurityIssue> out = new ArrayList<>();
    for (SecurityFinding f : new JavaSecurityScanner().scan(codebase)) {
      out.add(
          new ToolOutputs.SecurityIssue(
              f.testId(),
              f.testName(),
              f.file().toString(),
              f.lineNumber(),
              f.severity().name(),
              f.issueText()));
    }
    return out;
  }

  static List<ToolOutputs.ComplexityHotspot> collectComplexityHotspots(List<Path> files) {
    List<ToolOutputs.ComplexityHotspot> hotspots = new ArrayList<>();
    for (Path file : files) {
      try {
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(source);
        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
          int cc = cyclomaticOf(md);
          if (cc >= COMPLEXITY_THRESHOLD) {
            int line = md.getBegin().map(p -> p.line).orElse(0);
            hotspots.add(
                new ToolOutputs.ComplexityHotspot(md.getNameAsString(), file.toString(), line, cc));
          }
        }
      } catch (IOException | RuntimeException e) {
        // Match the Python source's silent skip on parse/read failures.
      }
    }
    hotspots.sort((a, b) -> Integer.compare(b.getComplexity(), a.getComplexity()));
    if (hotspots.size() > MAX_HOTSPOTS) {
      return new ArrayList<>(hotspots.subList(0, MAX_HOTSPOTS));
    }
    return hotspots;
  }

  /** Standard McCabe count — same decision-point set used by mfcqi-metrics' CC visitor. */
  private static int cyclomaticOf(MethodDeclaration md) {
    int[] cc = {1};
    md.walk(
        node -> {
          if (node instanceof com.github.javaparser.ast.stmt.IfStmt
              || node instanceof com.github.javaparser.ast.stmt.ForStmt
              || node instanceof com.github.javaparser.ast.stmt.ForEachStmt
              || node instanceof com.github.javaparser.ast.stmt.WhileStmt
              || node instanceof com.github.javaparser.ast.stmt.DoStmt
              || node instanceof com.github.javaparser.ast.stmt.CatchClause
              || node instanceof com.github.javaparser.ast.expr.ConditionalExpr) {
            cc[0]++;
          } else if (node instanceof com.github.javaparser.ast.stmt.SwitchEntry) {
            int labels = ((com.github.javaparser.ast.stmt.SwitchEntry) node).getLabels().size();
            if (labels > 0) {
              cc[0] += labels;
            }
          } else if (node instanceof com.github.javaparser.ast.expr.BinaryExpr) {
            com.github.javaparser.ast.expr.BinaryExpr.Operator op =
                ((com.github.javaparser.ast.expr.BinaryExpr) node).getOperator();
            if (op == com.github.javaparser.ast.expr.BinaryExpr.Operator.AND
                || op == com.github.javaparser.ast.expr.BinaryExpr.Operator.OR) {
              cc[0]++;
            }
          }
        });
    return cc[0];
  }

  static int countSourceLines(List<Path> files) {
    int total = 0;
    for (Path file : files) {
      try {
        for (String line :
            new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\\r?\\n", -1)) {
          if (!line.trim().isEmpty()) {
            total++;
          }
        }
      } catch (IOException e) {
        // Match Python: silent skip for unreadable files.
      }
    }
    return total;
  }

  /** Useful for tests and callers that want an empty placeholder. */
  public static ToolOutputs empty() {
    return ToolOutputs.builder()
        .bandit(Collections.emptyList())
        .complexity(Collections.emptyList())
        .build();
  }
}
