package com.integrallis.mfcqi.metrics;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.integrallis.mfcqi.core.Metric;
import com.integrallis.mfcqi.metrics.internal.CognitiveVisitor;
import com.integrallis.mfcqi.metrics.internal.ParsedFile;
import com.integrallis.mfcqi.metrics.internal.ParsedSources;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Cognitive Complexity (Campbell 2018, SonarSource) averaged across functions.
 *
 * <p>Direct port of {@code mfcqi/metrics/cognitive.py:CognitiveComplexity}. The Python
 * implementation delegates to the {@code cognitive_complexity} PyPI package, which itself
 * implements Campbell's algorithm; this Java implementation translates that package's algorithm
 * verbatim — see {@link CognitiveVisitor} for the per-AST-node mapping.
 *
 * <p>The Python {@code extract} skips files whose parent directory contains a part starting with
 * "test"; that filter is preserved here.
 */
public final class CognitiveComplexity extends Metric<CognitiveResult> {

  /** Default hotspot threshold (SonarLint recommendation). */
  public static final int DEFAULT_HOTSPOT_THRESHOLD = 15;

  private final int hotspotThreshold;

  public CognitiveComplexity() {
    this(DEFAULT_HOTSPOT_THRESHOLD);
  }

  public CognitiveComplexity(int hotspotThreshold) {
    this.hotspotThreshold = hotspotThreshold;
  }

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public CognitiveResult extract(Path codebase) {
    List<ParsedFile> parsed = ParsedSources.parseAll(codebase);
    if (parsed.isEmpty()) {
      return new CognitiveResult(0.0, Collections.emptyList(), Collections.emptyList());
    }

    long totalComplexity = 0;
    int functionCount = 0;
    List<CognitiveResult.FunctionEntry> functions = new ArrayList<>();
    List<CognitiveResult.FunctionEntry> hotspots = new ArrayList<>();

    for (ParsedFile file : parsed) {
      // Port of Python's "skip files whose parent dir contains a part starting with test":
      //   dir_parts = py_file.parent.parts
      //   if any(part.startswith("test") for part in dir_parts): continue
      if (anyParentStartsWithTest(file.path())) {
        continue;
      }

      for (MethodDeclaration md : file.compilationUnit().findAll(MethodDeclaration.class)) {
        if (!md.getBody().isPresent()) {
          continue;
        }
        int cc = CognitiveVisitor.complexityOf(md);
        CognitiveResult.FunctionEntry entry =
            entry(file, md.getNameAsString(), cc, md.getBegin().map(p -> p.line).orElse(0));
        functions.add(entry);
        totalComplexity += cc;
        functionCount++;
        if (cc >= hotspotThreshold) {
          hotspots.add(entry);
        }
      }
      for (ConstructorDeclaration cd :
          file.compilationUnit().findAll(ConstructorDeclaration.class)) {
        int cc = CognitiveVisitor.complexityOf(cd);
        CognitiveResult.FunctionEntry entry =
            entry(file, cd.getNameAsString(), cc, cd.getBegin().map(p -> p.line).orElse(0));
        functions.add(entry);
        totalComplexity += cc;
        functionCount++;
        if (cc >= hotspotThreshold) {
          hotspots.add(entry);
        }
      }
      for (InitializerDeclaration id :
          file.compilationUnit().findAll(InitializerDeclaration.class)) {
        int cc = CognitiveVisitor.complexityOf(id);
        CognitiveResult.FunctionEntry entry =
            entry(
                file,
                id.isStatic() ? "<clinit>" : "<init>",
                cc,
                id.getBegin().map(p -> p.line).orElse(0));
        functions.add(entry);
        totalComplexity += cc;
        functionCount++;
        if (cc >= hotspotThreshold) {
          hotspots.add(entry);
        }
      }
    }

    // Python: hotspots.sort(key=lambda x: x['complexity'], reverse=True)
    hotspots.sort(Comparator.comparingInt(CognitiveResult.FunctionEntry::complexity).reversed());

    double average = functionCount == 0 ? 0.0 : ((double) totalComplexity) / functionCount;
    return new CognitiveResult(average, functions, hotspots);
  }

  @Override
  public double normalize(CognitiveResult value) {
    // Verbatim port of Python piecewise-linear normalize() (SonarSource thresholds):
    //   <= 5  : 1.0 - avg/50
    //   <= 10 : 0.9 - (avg-5)/25
    //   <= 15 : 0.7 - (avg-10)/16.67
    //   <= 25 : 0.4 - (avg-15)/50
    //   else  : max(0, 0.2 - (avg-25)/125)
    double avg = value == null ? 0.0 : value.average();
    if (avg <= 5.0) {
      return 1.0 - (avg / 50.0);
    }
    if (avg <= 10.0) {
      return 0.9 - ((avg - 5.0) / 25.0);
    }
    if (avg <= 15.0) {
      return 0.7 - ((avg - 10.0) / 16.67);
    }
    if (avg <= 25.0) {
      return 0.4 - ((avg - 15.0) / 50.0);
    }
    return Math.max(0.0, 0.2 - ((avg - 25.0) / 125.0));
  }

  @Override
  public double getWeight() {
    return 0.75;
  }

  @Override
  public String getName() {
    return "Cognitive Complexity";
  }

  private static CognitiveResult.FunctionEntry entry(
      ParsedFile file, String name, int complexity, int line) {
    return new CognitiveResult.FunctionEntry(name, complexity, line, file.path().toString());
  }

  private static boolean anyParentStartsWithTest(Path file) {
    Path parent = file.getParent();
    if (parent == null) {
      return false;
    }
    for (Path part : parent) {
      if (part.toString().startsWith("test")) {
        return true;
      }
    }
    return false;
  }
}
