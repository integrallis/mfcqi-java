package com.integrallis.mfcqi.smells;

import com.integrallis.mfcqi.core.JavaSourceFiles;
import com.integrallis.mfcqi.core.Metric;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Code Smell Density — weighted smell count per 1K lines of code, normalized to {@code [0, 1]}.
 *
 * <p>Direct port of {@code mfcqi/metrics/code_smell.py:CodeSmellDensity}. The Python implementation
 * runs every registered detector through {@link SmellAggregator}, takes the deduplicated
 * weighted-by-severity counts per category, applies per-category weights from {@code
 * code_smells.md} ({@code architectural=0.45, design=0.45, implementation=0.35, test=0.20}), then
 * normalizes per 1K LOC and runs through a piecewise normalize curve.
 *
 * <p>Java equivalence preserves every constant verbatim — only the LOC counter is adapted (Python
 * skips lines starting with {@code #}; the Java port skips lines starting with {@code //}, matching
 * the same lazy treatment of multi-line comments used in {@link
 * com.integrallis.mfcqi.smells.JavaSmellDetector}'s sibling code-counting rules).
 */
public final class CodeSmellDensity extends Metric<Double> {

  private final List<SmellDetector> detectors;
  private final SmellAggregator aggregator;

  public CodeSmellDensity() {
    this(Collections.emptyList());
  }

  public CodeSmellDensity(List<SmellDetector> detectors) {
    this.detectors = Collections.unmodifiableList(Objects.requireNonNull(detectors, "detectors"));
    this.aggregator = new SmellAggregator(this.detectors);
  }

  /** Convenience: detect-all default — JavaSmellDetector + JavaTestSmellDetector. */
  public static CodeSmellDensity withDefaultDetectors() {
    return new CodeSmellDensity(
        Arrays.asList(new JavaSmellDetector(), new JavaTestSmellDetector()));
  }

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    if (detectors.isEmpty()) {
      // Mirrors Python: no detectors => no smells detected.
      return 0.0;
    }
    Map<SmellCategory, Double> weighted = aggregator.weightedCountByCategory(codebase);
    Map<SmellCategory, Double> categoryWeights = categoryWeights();

    double total = 0.0;
    for (Map.Entry<SmellCategory, Double> e : weighted.entrySet()) {
      double w = categoryWeights.getOrDefault(e.getKey(), 0.35);
      total += e.getValue() * w;
    }
    int loc = countLinesOfCode(codebase);
    if (loc == 0) {
      return 0.0;
    }
    return (total / loc) * 1000.0;
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port of Python piecewise normalize() (smells/KLOC):
    //   v <= 0  : 1.0
    //   v >= 50 : 0.0
    //   v <= 5  : 1.0 - (v/5) * 0.2          [1.0 -> 0.8]
    //   v <= 10 : 0.8 - ((v-5)/5) * 0.2      [0.8 -> 0.6]
    //   v <= 20 : 0.6 - ((v-10)/10) * 0.3    [0.6 -> 0.3]
    //   else    : max(0, 0.3 - ((v-20)/30) * 0.3) [0.3 -> 0.0]
    if (value == null) {
      return 1.0;
    }
    double v = value;
    if (v <= 0.0) {
      return 1.0;
    }
    if (v >= 50.0) {
      return 0.0;
    }
    if (v <= 5.0) {
      return 1.0 - (v / 5.0) * 0.2;
    }
    if (v <= 10.0) {
      return 0.8 - ((v - 5.0) / 5.0) * 0.2;
    }
    if (v <= 20.0) {
      return 0.6 - ((v - 10.0) / 10.0) * 0.3;
    }
    return Math.max(0.0, 0.3 - ((v - 20.0) / 30.0) * 0.3);
  }

  @Override
  public double getWeight() {
    return 0.5;
  }

  @Override
  public String getName() {
    return "Code Smell Density";
  }

  /** Verbatim port of {@code _get_category_weights}. */
  static Map<SmellCategory, Double> categoryWeights() {
    Map<SmellCategory, Double> w = new EnumMap<>(SmellCategory.class);
    w.put(SmellCategory.ARCHITECTURAL, 0.45);
    w.put(SmellCategory.DESIGN, 0.45);
    w.put(SmellCategory.IMPLEMENTATION, 0.35);
    w.put(SmellCategory.TEST, 0.20);
    return w;
  }

  /** Port of Python's {@code _count_lines_of_code} adapted for Java line-comment marker. */
  static int countLinesOfCode(Path codebase) {
    int total = 0;
    for (Path file : JavaSourceFiles.findAll(codebase)) {
      try {
        for (String line :
            new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\\r?\\n", -1)) {
          String s = line.trim();
          if (!s.isEmpty() && !s.startsWith("//")) {
            total++;
          }
        }
      } catch (IOException e) {
        // Match Python: silent skip for unreadable files.
      }
    }
    return total;
  }
}
