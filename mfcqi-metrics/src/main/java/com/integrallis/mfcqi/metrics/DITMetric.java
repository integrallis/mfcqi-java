package com.integrallis.mfcqi.metrics;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.integrallis.mfcqi.core.Metric;
import com.integrallis.mfcqi.metrics.internal.ParsedFile;
import com.integrallis.mfcqi.metrics.internal.ParsedSources;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Depth of Inheritance Tree (DIT) — longest inheritance path from any class to the root, computed
 * per file.
 *
 * <p>Direct port of {@code mfcqi/metrics/dit.py:DITMetric}. The Python implementation:
 *
 * <ul>
 *   <li>Builds a map {@code class_name -> parent_class_name} per file from {@code ast.ClassDef
 *       bases[0]} (single-base only)
 *   <li>If the parent is not a class declared in the same file, DIT = 1 (external base)
 *   <li>Otherwise, DIT = 1 + parent's DIT, computed recursively with cycle protection
 *   <li>Returns the max DIT across all classes (across all files)
 * </ul>
 *
 * <p>Java equivalence: Python's {@code class Foo(Bar):} maps to Java's {@code extends Bar}. Python
 * only inspects {@code bases[0]}; the Java port follows the same convention by reading {@link
 * ClassOrInterfaceDeclaration#getExtendedTypes()} and using its first entry. Implemented interfaces
 * are NOT counted (matches Python's behavior of inspecting only the first base).
 */
public final class DITMetric extends Metric<Double> {

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    List<ParsedFile> files = ParsedSources.parseAll(codebase);
    int globalMax = 0;
    for (ParsedFile file : files) {
      int fileMax = maxDitInFile(file);
      if (fileMax > globalMax) {
        globalMax = fileMax;
      }
    }
    return (double) globalMax;
  }

  private static int maxDitInFile(ParsedFile file) {
    Map<String, String> inheritanceMap = new HashMap<>();
    Set<String> declaredClasses = new HashSet<>();

    for (ClassOrInterfaceDeclaration cls :
        file.compilationUnit().findAll(ClassOrInterfaceDeclaration.class)) {
      String name = cls.getNameAsString();
      declaredClasses.add(name);
      // Python inspects bases[0] only — port that literal behavior.
      if (!cls.getExtendedTypes().isEmpty()) {
        ClassOrInterfaceType firstBase = cls.getExtendedTypes(0);
        inheritanceMap.put(name, firstBase.getNameAsString());
      }
    }

    if (declaredClasses.isEmpty()) {
      return 0;
    }

    Map<String, Integer> ditValues = new HashMap<>();
    int max = 0;
    for (String cls : declaredClasses) {
      int d = computeDit(cls, declaredClasses, inheritanceMap, ditValues, new HashSet<>());
      if (d > max) {
        max = d;
      }
    }
    return max;
  }

  /** Recursive DIT computation with memoization and cycle protection — port of Python helper. */
  private static int computeDit(
      String cls,
      Set<String> declared,
      Map<String, String> inheritance,
      Map<String, Integer> cache,
      Set<String> visited) {
    if (visited.contains(cls)) {
      return 0;
    }
    Integer cached = cache.get(cls);
    if (cached != null) {
      return cached;
    }
    visited.add(cls);

    String parent = inheritance.get(cls);
    if (parent == null) {
      cache.put(cls, 0);
      return 0;
    }
    if (!declared.contains(parent)) {
      // External parent — Python returns 1.
      cache.put(cls, 1);
      return 1;
    }
    int parentDit = computeDit(parent, declared, inheritance, cache, new HashSet<>(visited));
    int result = 1 + parentDit;
    cache.put(cls, result);
    return result;
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port of Python normalize() (Python-calibrated DIT thresholds, Oct 2025):
    //   v <= 3   : 1.0
    //   v <= 6   : 1.0 - 0.30*(v-3)/3
    //   v <= 10  : 0.70 - 0.30*(v-6)/4
    //   else     : max(0, 0.40 - 0.40*(v-10)/5)
    if (value == null) {
      return 1.0;
    }
    double v = value;
    if (v <= 3.0) {
      return 1.0;
    }
    if (v <= 6.0) {
      return 1.0 - 0.30 * (v - 3.0) / 3.0;
    }
    if (v <= 10.0) {
      return 0.70 - 0.30 * (v - 6.0) / 4.0;
    }
    return Math.max(0.0, 0.40 - 0.40 * (v - 10.0) / 5.0);
  }

  @Override
  public double getWeight() {
    return 0.6;
  }

  @Override
  public String getName() {
    return "dit";
  }
}
