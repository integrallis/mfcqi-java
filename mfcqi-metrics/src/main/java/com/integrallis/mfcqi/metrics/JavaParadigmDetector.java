package com.integrallis.mfcqi.metrics;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.integrallis.mfcqi.core.Paradigm;
import com.integrallis.mfcqi.core.ParadigmDetection;
import com.integrallis.mfcqi.core.ParadigmDetector;
import com.integrallis.mfcqi.metrics.internal.ParsedFile;
import com.integrallis.mfcqi.metrics.internal.ParsedSources;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JavaParser-backed {@link ParadigmDetector} — direct port of {@code
 * mfcqi/core/paradigm_detector.py}.
 *
 * <p>The Python algorithm aggregates per-file signals (class density, method ratio, inheritance
 * usage, encapsulation, composition, properties, OO/procedural import patterns), combines them into
 * a single {@code oo_score}, and classifies as {@code STRONG_OO} (≥ 0.7), {@code MIXED_OO} (≥ 0.4),
 * {@code WEAK_OO} (≥ 0.2), or {@code PROCEDURAL}. Java translation:
 *
 * <ul>
 *   <li>{@code total_classes} — every class/interface/enum/annotation declaration
 *   <li>{@code class_methods} — instance methods of any type
 *   <li>{@code standalone_functions} — Java has no free functions, so this is approximated by
 *       counting {@code public static} methods on classes named with a "Util"/"Utils"/"Helper"
 *       suffix (the typical Java analog of Python's free functions). For pure procedural Java this
 *       still produces a reasonable signal; for class-heavy code it is near zero, which matches the
 *       Python reference's effective behavior on pure-OO Python.
 *   <li>{@code inheritance_count} — classes with a non-empty {@code extends} clause
 *   <li>{@code multiple_inheritance} — implementations with ≥ 2 interface types
 *   <li>{@code private_methods} — Python's "{@code _name}" → Java's {@code private} modifier
 *   <li>{@code properties} — Python {@code @property} decorator → Java JavaBean accessors (methods
 *       named {@code getX}/{@code setX}/{@code isX} with conventional shape)
 *   <li>{@code composition_count} — class-level field declarations (the most direct analog of
 *       Python's "class-body assignment" composition heuristic)
 *   <li>{@code oo_imports} / {@code procedural_imports} — Java-specific lists; see constants below.
 *       Documented divergence from Python's package list.
 * </ul>
 *
 * <p>The {@code oo_score} formula and classification thresholds are translated verbatim.
 */
public final class JavaParadigmDetector implements ParadigmDetector {

  /**
   * Imports leaning OO-style. Adapted from Python's set ({@code abc}, {@code dataclasses}, {@code
   * typing}, …) — these are Java packages whose presence usually signals object-oriented design or
   * framework use.
   */
  private static final Set<String> OO_IMPORT_PREFIXES;

  /**
   * Imports leaning procedural/functional. Adapted from Python's set ({@code functools}, {@code
   * itertools}, {@code math}, …).
   */
  private static final Set<String> PROCEDURAL_IMPORT_PREFIXES;

  static {
    Set<String> oo = new HashSet<>();
    Collections.addAll(
        oo,
        "java.beans",
        "java.lang.reflect",
        "javax.persistence",
        "jakarta.persistence",
        "org.springframework.stereotype",
        "com.fasterxml.jackson.annotation");
    OO_IMPORT_PREFIXES = Collections.unmodifiableSet(oo);

    Set<String> proc = new HashSet<>();
    Collections.addAll(
        proc,
        "java.util.stream",
        "java.util.function",
        "java.util.Arrays",
        "java.util.Collections",
        "java.util.Comparator");
    PROCEDURAL_IMPORT_PREFIXES = Collections.unmodifiableSet(proc);
  }

  @Override
  public ParadigmDetection detect(Path codebase) {
    Counters c = new Counters();
    List<ParsedFile> files = ParsedSources.parseAll(codebase, true /* exclude tests */);
    for (ParsedFile file : files) {
      analyzeFile(file, c);
    }
    double ooScore = computeOoScore(c);
    Paradigm paradigm = classify(ooScore);

    Map<String, Double> signals = new LinkedHashMap<>();
    signals.put("total_lines", (double) c.totalLines);
    signals.put("total_classes", (double) c.totalClasses);
    signals.put("total_functions", (double) c.totalFunctions);
    signals.put("class_methods", (double) c.classMethods);
    signals.put("standalone_functions", (double) c.standaloneFunctions);
    signals.put("inheritance_count", (double) c.inheritanceCount);
    signals.put("private_methods", (double) c.privateMethods);
    signals.put("properties", (double) c.properties);
    return new ParadigmDetection(paradigm, ooScore, signals);
  }

  private static void analyzeFile(ParsedFile file, Counters c) {
    c.totalLines += file.source().split("\\r?\\n", -1).length;

    for (TypeDeclaration<?> td : file.compilationUnit().findAll(TypeDeclaration.class)) {
      boolean isClassOrInterface = td instanceof ClassOrInterfaceDeclaration;
      boolean isEnum = td instanceof EnumDeclaration;
      boolean isAnnotation = td instanceof AnnotationDeclaration;
      if (!(isClassOrInterface || isEnum || isAnnotation)) {
        continue;
      }
      c.totalClasses++;

      // Inheritance / multiple inheritance — Python: bases / multiple bases.
      if (td instanceof ClassOrInterfaceDeclaration) {
        ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) td;
        if (!cid.getExtendedTypes().isEmpty()) {
          c.inheritanceCount++;
        }
        if (cid.getImplementedTypes().size() > 1) {
          c.multipleInheritance++;
        }
        // Composition — Python heuristic: assignments in class body. Java analog: field
        // declarations that hold object references (any field declaration).
        for (FieldDeclaration fd : cid.getFields()) {
          c.compositionCount += fd.getVariables().size();
        }
      }

      // Methods.
      String typeName = td.getNameAsString();
      boolean isUtilityType = isUtilityName(typeName);
      for (MethodDeclaration md : td.findAll(MethodDeclaration.class)) {
        c.totalFunctions++;
        // Java equivalent of Python's "in class" check: every method is in a type.
        c.classMethods++;
        if (md.isPrivate()) {
          c.privateMethods++;
        }
        if (isJavaBeanAccessor(md)) {
          c.properties++;
        }
        // Python's "standalone_functions" approximation: public static methods on a *Util[s]/
        // *Helper class. This is the closest Java analog to Python's free functions.
        if (isUtilityType && md.isStatic() && md.isPublic()) {
          c.standaloneFunctions++;
        }
      }
    }

    for (ImportDeclaration imp : file.compilationUnit().getImports()) {
      String name = imp.getNameAsString();
      if (matchesAnyPrefix(name, OO_IMPORT_PREFIXES)) {
        c.ooImports++;
      } else if (matchesAnyPrefix(name, PROCEDURAL_IMPORT_PREFIXES)) {
        c.proceduralImports++;
      }
    }
  }

  private static boolean isUtilityName(String name) {
    return name.endsWith("Util") || name.endsWith("Utils") || name.endsWith("Helper");
  }

  /** Loose JavaBean accessor heuristic: getX(), isX(), setX(...) with appropriate arity. */
  private static boolean isJavaBeanAccessor(MethodDeclaration md) {
    String n = md.getNameAsString();
    int params = md.getParameters().size();
    if ((n.startsWith("get") && n.length() > 3 && Character.isUpperCase(n.charAt(3)))
        && params == 0) {
      return true;
    }
    if ((n.startsWith("is") && n.length() > 2 && Character.isUpperCase(n.charAt(2)))
        && params == 0) {
      return true;
    }
    return n.startsWith("set")
        && n.length() > 3
        && Character.isUpperCase(n.charAt(3))
        && params == 1;
  }

  private static boolean matchesAnyPrefix(String name, Set<String> prefixes) {
    for (String prefix : prefixes) {
      if (name.equals(prefix) || name.startsWith(prefix + ".")) {
        return true;
      }
    }
    return false;
  }

  /** Verbatim port of {@code _calculate_oo_score}. */
  private static double computeOoScore(Counters c) {
    if (c.totalLines == 0) {
      return 0.0;
    }
    double classDensity =
        Math.min((((double) c.totalClasses) / Math.max(1, c.totalLines)) * 1000.0 / 5.0, 1.0);
    double methodRatio =
        Math.min(((double) c.classMethods) / Math.max(1, c.standaloneFunctions), 1.0);
    double inheritanceUsage =
        Math.min(((double) c.inheritanceCount) / Math.max(1, c.totalClasses), 1.0);
    double encapsulation = Math.min(((double) c.privateMethods) / Math.max(1, c.classMethods), 0.3);
    double composition = Math.min(((double) c.compositionCount) / Math.max(1, c.totalClasses), 0.2);
    double properties = Math.min(((double) c.properties) / Math.max(1, c.classMethods), 0.2);

    double importScore = 0.0;
    if (c.ooImports > 0) {
      importScore += 0.1;
    }
    if (c.proceduralImports > c.ooImports) {
      importScore -= 0.1;
    }

    double score =
        classDensity * 0.3
            + methodRatio * 0.25
            + inheritanceUsage * 0.2
            + encapsulation
            + composition
            + properties
            + importScore;
    return Math.max(0.0, Math.min(1.0, score));
  }

  /** Verbatim port of {@code _classify_paradigm}. */
  private static Paradigm classify(double ooScore) {
    if (ooScore >= 0.7) {
      return Paradigm.STRONG_OO;
    }
    if (ooScore >= 0.4) {
      return Paradigm.MIXED_OO;
    }
    if (ooScore >= 0.2) {
      return Paradigm.WEAK_OO;
    }
    return Paradigm.PROCEDURAL;
  }

  private static final class Counters {
    int totalLines;
    int totalClasses;
    int totalFunctions;
    int classMethods;
    int standaloneFunctions;
    int inheritanceCount;
    int multipleInheritance;
    int privateMethods;
    int properties;
    int compositionCount;
    int ooImports;
    int proceduralImports;
  }
}
