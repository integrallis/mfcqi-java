package com.integrallis.mfcqi.smells;

import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.integrallis.mfcqi.smells.internal.SourceTrees;
import com.integrallis.mfcqi.smells.internal.SourceTrees.ParsedSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Production code-smell detector — Java analog of {@code
 * mfcqi/smell_detection/pyexamine.py:PyExamineDetector}.
 *
 * <p>The Python adapter shells out to the {@code analyze_code_quality} CLI (PyExamine, MSR 2025).
 * Since no equivalent Java CLI exists, this implementation detects the same classic smell types
 * directly via JavaParser AST analysis. Detected smells use category and severity mappings that
 * mirror the Python's {@code _map_category}/{@code _map_severity} heuristics.
 *
 * <p>Implemented detections:
 *
 * <ul>
 *   <li><b>Long Method</b> ({@code IMPLEMENTATION}, {@code MEDIUM}) — method body &gt; 45 lines
 *   <li><b>Long Parameter List</b> ({@code IMPLEMENTATION}, {@code LOW}) — method/constructor with
 *       &gt; 5 parameters
 *   <li><b>God Class</b> ({@code DESIGN}, {@code HIGH}) — class with &gt; 20 methods or &gt; 15
 *       fields
 *   <li><b>Deep Inheritance Warning</b> ({@code DESIGN}, {@code MEDIUM}) — class extends a base
 *       within the same file at depth ≥ 4 (rough source-only heuristic)
 *   <li><b>High Coupling Warning</b> ({@code DESIGN}, {@code MEDIUM}) — class with &gt; 10 distinct
 *       fan-out method calls or unique types referenced
 * </ul>
 */
public final class JavaSmellDetector implements SmellDetector {

  /** Default thresholds matching common refactoring guidance (Fowler) and the PyExamine paper. */
  public static final int DEFAULT_LONG_METHOD_LINES = 45;

  public static final int DEFAULT_LONG_PARAMETER_LIST = 5;
  public static final int DEFAULT_GOD_CLASS_METHODS = 20;
  public static final int DEFAULT_GOD_CLASS_FIELDS = 15;
  public static final int DEFAULT_DEEP_INHERITANCE = 4;
  public static final int DEFAULT_HIGH_COUPLING = 10;

  private final int longMethodLines;
  private final int longParameterList;
  private final int godClassMethods;
  private final int godClassFields;
  private final int deepInheritance;
  private final int highCoupling;

  public JavaSmellDetector() {
    this(
        DEFAULT_LONG_METHOD_LINES,
        DEFAULT_LONG_PARAMETER_LIST,
        DEFAULT_GOD_CLASS_METHODS,
        DEFAULT_GOD_CLASS_FIELDS,
        DEFAULT_DEEP_INHERITANCE,
        DEFAULT_HIGH_COUPLING);
  }

  public JavaSmellDetector(
      int longMethodLines,
      int longParameterList,
      int godClassMethods,
      int godClassFields,
      int deepInheritance,
      int highCoupling) {
    this.longMethodLines = longMethodLines;
    this.longParameterList = longParameterList;
    this.godClassMethods = godClassMethods;
    this.godClassFields = godClassFields;
    this.deepInheritance = deepInheritance;
    this.highCoupling = highCoupling;
  }

  @Override
  public String name() {
    return "java-smells";
  }

  @Override
  public List<Smell> detect(Path codebase) {
    List<Smell> out = new ArrayList<>();
    for (ParsedSource src : SourceTrees.parseAll(codebase)) {
      out.addAll(detectInFile(src));
    }
    return out;
  }

  private List<Smell> detectInFile(ParsedSource src) {
    List<Smell> out = new ArrayList<>();
    String relPath = src.path().toString();

    for (MethodDeclaration md : src.compilationUnit().findAll(MethodDeclaration.class)) {
      int lines = lineCount(md);
      if (lines >= longMethodLines) {
        out.add(longMethodSmell(relPath, md, lines));
      }
      if (md.getParameters().size() > longParameterList) {
        out.add(
            longParameterSmell(
                relPath, md.getNameAsString(), md.getParameters().size(), lineOf(md)));
      }
    }
    for (ConstructorDeclaration cd : src.compilationUnit().findAll(ConstructorDeclaration.class)) {
      if (cd.getParameters().size() > longParameterList) {
        out.add(
            longParameterSmell(
                relPath, cd.getNameAsString(), cd.getParameters().size(), lineOf(cd)));
      }
    }

    // God Class / Deep Inheritance / High Coupling all operate at the class declaration level.
    java.util.Map<String, Integer> ditCache = new java.util.HashMap<>();
    java.util.Set<String> declaredClasses = new java.util.HashSet<>();
    java.util.Map<String, String> firstParent = new java.util.HashMap<>();
    for (ClassOrInterfaceDeclaration cls :
        src.compilationUnit().findAll(ClassOrInterfaceDeclaration.class)) {
      declaredClasses.add(cls.getNameAsString());
      if (!cls.getExtendedTypes().isEmpty()) {
        firstParent.put(cls.getNameAsString(), cls.getExtendedTypes(0).getNameAsString());
      }
    }
    for (ClassOrInterfaceDeclaration cls :
        src.compilationUnit().findAll(ClassOrInterfaceDeclaration.class)) {
      int methodCount = cls.getMethods().size();
      int fieldCount = countFields(cls);
      if (methodCount > godClassMethods || fieldCount > godClassFields) {
        out.add(
            godClassSmell(relPath, cls.getNameAsString(), methodCount, fieldCount, lineOf(cls)));
      }
      int depth =
          ditOf(
              cls.getNameAsString(),
              declaredClasses,
              firstParent,
              ditCache,
              new java.util.HashSet<>());
      if (depth >= deepInheritance) {
        out.add(deepInheritanceSmell(relPath, cls.getNameAsString(), depth, lineOf(cls)));
      }
      int coupling = couplingOf(cls);
      if (coupling > highCoupling) {
        out.add(highCouplingSmell(relPath, cls.getNameAsString(), coupling, lineOf(cls)));
      }
    }
    return out;
  }

  private static int lineCount(MethodDeclaration md) {
    if (!md.getBody().isPresent()) {
      return 0;
    }
    int begin = md.getBody().get().getBegin().map(p -> p.line).orElse(0);
    int end = md.getBody().get().getEnd().map(p -> p.line).orElse(0);
    return Math.max(0, end - begin + 1);
  }

  private static int lineOf(com.github.javaparser.ast.Node node) {
    return node.getBegin().map((Position p) -> p.line).orElse(0);
  }

  private static int countFields(ClassOrInterfaceDeclaration cls) {
    int total = 0;
    for (FieldDeclaration fd : cls.getFields()) {
      total += fd.getVariables().size();
    }
    return total;
  }

  /** Same algorithm as DITMetric — limited to the current file. */
  private int ditOf(
      String cls,
      java.util.Set<String> declared,
      java.util.Map<String, String> firstParent,
      java.util.Map<String, Integer> cache,
      java.util.Set<String> visited) {
    if (visited.contains(cls)) {
      return 0;
    }
    Integer cached = cache.get(cls);
    if (cached != null) {
      return cached;
    }
    visited.add(cls);
    String parent = firstParent.get(cls);
    if (parent == null) {
      cache.put(cls, 0);
      return 0;
    }
    if (!declared.contains(parent)) {
      cache.put(cls, 1);
      return 1;
    }
    int depth = 1 + ditOf(parent, declared, firstParent, cache, new java.util.HashSet<>(visited));
    cache.put(cls, depth);
    return depth;
  }

  /** java.lang types (implicitly imported, so not in the import table) treated as non-coupling. */
  private static final Set<String> JAVA_LANG_BUILTINS =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "String",
                  "Object",
                  "Integer",
                  "Long",
                  "Double",
                  "Float",
                  "Boolean",
                  "Byte",
                  "Short",
                  "Character",
                  "Number",
                  "CharSequence",
                  "Math",
                  "System",
                  "Thread",
                  "Runnable",
                  "Throwable",
                  "Exception",
                  "RuntimeException",
                  "Error",
                  "Iterable",
                  "Comparable",
                  "Class",
                  "Void",
                  "StringBuilder")));

  /**
   * Coupling = number of distinct external type names referenced, EXCLUDING JDK/library types
   * (java.lang built-ins plus anything imported from the {@code java}, {@code javax}, or {@code
   * jakarta} namespaces). Without this exclusion the detector flags ordinary, well-factored Java
   * classes as "high coupling" merely for naming JDK types (Path, Stream, List, IOException,
   * Optional, …) in their signatures — which made this the single largest contributor to smell
   * density on clean code.
   */
  private static int couplingOf(ClassOrInterfaceDeclaration cls) {
    Set<String> jdk = new HashSet<>(JAVA_LANG_BUILTINS);
    cls.findCompilationUnit().ifPresent(cu -> jdk.addAll(jdkImportedSimpleNames(cu)));
    Set<String> coupled = new HashSet<>();
    for (ClassOrInterfaceType base : cls.getExtendedTypes()) {
      coupled.add(base.getNameAsString());
    }
    for (ClassOrInterfaceType base : cls.getImplementedTypes()) {
      coupled.add(base.getNameAsString());
    }
    for (FieldDeclaration fd : cls.getFields()) {
      for (com.github.javaparser.ast.body.VariableDeclarator vd : fd.getVariables()) {
        if (vd.getType() instanceof ClassOrInterfaceType) {
          coupled.add(((ClassOrInterfaceType) vd.getType()).getNameAsString());
        }
      }
    }
    for (MethodDeclaration md : cls.getMethods()) {
      if (md.getType() instanceof ClassOrInterfaceType) {
        coupled.add(((ClassOrInterfaceType) md.getType()).getNameAsString());
      }
      for (com.github.javaparser.ast.body.Parameter p : md.getParameters()) {
        if (p.getType() instanceof ClassOrInterfaceType) {
          coupled.add(((ClassOrInterfaceType) p.getType()).getNameAsString());
        }
      }
    }
    for (MethodCallExpr call : cls.findAll(MethodCallExpr.class)) {
      if (call.getScope().isPresent() && call.getScope().get() instanceof NameExpr) {
        coupled.add(((NameExpr) call.getScope().get()).getNameAsString());
      }
    }
    coupled.remove(cls.getNameAsString());
    coupled.removeAll(jdk);
    return coupled.size();
  }

  /** Simple names imported from the JDK / Jakarta namespaces. */
  private static Set<String> jdkImportedSimpleNames(CompilationUnit cu) {
    Set<String> names = new HashSet<>();
    for (ImportDeclaration imp : cu.getImports()) {
      if (imp.isAsterisk()) {
        continue;
      }
      String fqn = imp.getNameAsString();
      if (fqn.startsWith("java.") || fqn.startsWith("javax.") || fqn.startsWith("jakarta.")) {
        int dot = fqn.lastIndexOf('.');
        names.add(dot < 0 ? fqn : fqn.substring(dot + 1));
      }
    }
    return names;
  }

  private Smell longMethodSmell(String filePath, MethodDeclaration md, int lines) {
    return new Smell(
        "LONG_METHOD",
        "Long Method",
        SmellCategory.IMPLEMENTATION,
        SmellSeverity.MEDIUM,
        filePath + ":" + lineOf(md),
        name(),
        "Method '"
            + md.getNameAsString()
            + "' has "
            + lines
            + " lines (threshold: "
            + longMethodLines
            + ").");
  }

  private Smell longParameterSmell(String filePath, String methodName, int paramCount, int line) {
    return new Smell(
        "LONG_PARAMETER_LIST",
        "Long Parameter List",
        SmellCategory.IMPLEMENTATION,
        SmellSeverity.LOW,
        filePath + ":" + line,
        name(),
        "'"
            + methodName
            + "' takes "
            + paramCount
            + " parameters (threshold: "
            + longParameterList
            + ").");
  }

  private Smell godClassSmell(
      String filePath, String className, int methodCount, int fieldCount, int line) {
    return new Smell(
        "GOD_CLASS",
        "God Class",
        SmellCategory.DESIGN,
        SmellSeverity.HIGH,
        filePath + ":" + line,
        name(),
        "Class '"
            + className
            + "' has "
            + methodCount
            + " methods and "
            + fieldCount
            + " fields — likely doing too much.");
  }

  private Smell deepInheritanceSmell(String filePath, String className, int depth, int line) {
    return new Smell(
        "DEEP_INHERITANCE",
        "Deep Inheritance",
        SmellCategory.DESIGN,
        SmellSeverity.MEDIUM,
        filePath + ":" + line,
        name(),
        "Class '"
            + className
            + "' has inheritance depth "
            + depth
            + " (threshold: "
            + deepInheritance
            + ").");
  }

  private Smell highCouplingSmell(String filePath, String className, int coupling, int line) {
    return new Smell(
        "HIGH_COUPLING",
        "High Coupling",
        SmellCategory.DESIGN,
        SmellSeverity.MEDIUM,
        filePath + ":" + line,
        name(),
        "Class '"
            + className
            + "' references "
            + coupling
            + " distinct external types (threshold: "
            + highCoupling
            + ").");
  }
}
