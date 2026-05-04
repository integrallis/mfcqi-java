package com.integrallis.mfcqi.metrics;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.integrallis.mfcqi.core.Metric;
import com.integrallis.mfcqi.metrics.internal.ParsedFile;
import com.integrallis.mfcqi.metrics.internal.ParsedSources;
import java.nio.file.Path;
import java.util.List;

/**
 * Method Hiding Factor (MHF) — ratio of "private" methods to total methods, max across classes.
 *
 * <p>Direct port of {@code mfcqi/metrics/mhf.py:MHFMetric}. Python counts a method as "private"
 * when its name begins with one underscore (and not two — those are name-mangled or magic). Java
 * has explicit access modifiers, so this port maps Python's intent ({@code _foo} = "internal") to
 * Java's strictest analog: the {@code private} modifier. {@code protected} methods are NOT counted
 * as private (Python lacks the protected concept; counting them would over-credit encapsulation).
 *
 * <p>Magic methods in Python ({@code __init__}, {@code __str__}, …) are excluded from the "private"
 * count by the Python source. The Java equivalents — constructors, common Object overrides like
 * {@code toString}/{@code equals}/{@code hashCode} — are method declarations and are included in
 * the {@code total} count, mirroring the Python behavior of including {@code __init__} in {@code
 * total_methods}.
 *
 * <p>Returns the maximum MHF across all classes (mirrors {@code get_max_mhf}).
 */
public final class MHFMetric extends Metric<Double> {

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    List<ParsedFile> files = ParsedSources.parseAll(codebase);
    double max = 0.0;
    for (ParsedFile file : files) {
      for (ClassOrInterfaceDeclaration cls :
          file.compilationUnit().findAll(ClassOrInterfaceDeclaration.class)) {
        double mhf = mhfForClass(cls);
        if (mhf > max) {
          max = mhf;
        }
      }
    }
    return max;
  }

  private static double mhfForClass(ClassOrInterfaceDeclaration cls) {
    int total = 0;
    int privateCount = 0;
    for (MethodDeclaration md : cls.getMethods()) {
      total++;
      if (md.isPrivate()) {
        privateCount++;
      }
    }
    if (total == 0) {
      return 0.0;
    }
    return ((double) privateCount) / total;
  }

  @Override
  public double normalize(Double value) {
    // Python normalize() returns the value unchanged — MHF is already in [0, 1].
    if (value == null) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, value));
  }

  @Override
  public double getWeight() {
    return 0.55;
  }

  @Override
  public String getName() {
    return "mhf";
  }
}
