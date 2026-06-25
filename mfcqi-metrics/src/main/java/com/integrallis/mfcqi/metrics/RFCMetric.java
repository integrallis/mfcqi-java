package com.integrallis.mfcqi.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.integrallis.mfcqi.core.Metric;
import com.integrallis.mfcqi.metrics.internal.ParsedFile;
import com.integrallis.mfcqi.metrics.internal.ParsedSources;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Response For Class (RFC) — number of local methods plus number of distinct remote method names
 * called from within the class.
 *
 * <p>Direct port of {@code mfcqi/metrics/rfc.py:RFCMetric}. The Python implementation walks each
 * class with {@link com.github.javaparser.ast.body.MethodDeclaration}-equivalent collection and
 * call-name harvesting (the {@code RFCVisitor.visit_Call} branch counts {@code obj.method()}-style
 * invocations by reading {@code node.func.attr}). The Java analog walks each class declaration's
 * methods plus every {@link MethodCallExpr} contained in its tree.
 *
 * <p>Returns the maximum RFC across all classes (mirrors {@code get_max_rfc}). Normalization is the
 * verbatim piecewise-linear curve from the Python source (Python-calibrated, October 2025).
 */
public final class RFCMetric extends Metric<Double> {

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    List<ParsedFile> files = ParsedSources.parseAll(codebase);
    int max = 0;
    for (ParsedFile file : files) {
      for (ClassOrInterfaceDeclaration cls :
          file.compilationUnit().findAll(ClassOrInterfaceDeclaration.class)) {
        int rfc = rfcForClass(cls);
        if (rfc > max) {
          max = rfc;
        }
      }
    }
    return (double) max;
  }

  /**
   * RFC = local method count + count of distinct remote method names invoked by the class.
   *
   * <p>Two details mirror the Python reference's {@code RFCVisitor} exactly (without them the Java
   * raw value runs systematically higher than Python's, deflating the normalized score):
   *
   * <ul>
   *   <li><b>Scoped calls only.</b> Python's {@code visit_Call} guards on {@code hasattr(node.func,
   *       "attr")}, i.e. it counts {@code obj.method()} but NOT bare {@code method()}/{@code
   *       helper()} calls. The Java analog keeps only calls with a scope.
   *   <li><b>No nested-type calls.</b> Python resets its call set on entering each {@code
   *       ClassDef}, so a class does not absorb calls made inside a nested type. JavaParser's
   *       {@code findAll} recurses, so we drop calls whose nearest enclosing type is not this
   *       class.
   * </ul>
   */
  private static int rfcForClass(ClassOrInterfaceDeclaration cls) {
    Set<String> localMethods = new HashSet<>();
    for (MethodDeclaration md : cls.getMethods()) {
      localMethods.add(md.getNameAsString());
    }
    Set<String> remoteCalls = new HashSet<>();
    for (MethodCallExpr call : cls.findAll(MethodCallExpr.class)) {
      if (!call.getScope().isPresent()) {
        continue; // bare call (e.g. helper()) — Python's attr guard excludes these
      }
      if (!enclosingTypeIs(call, cls)) {
        continue; // call lives inside a nested type — Python attributes it to that type
      }
      remoteCalls.add(call.getNameAsString());
    }
    return localMethods.size() + remoteCalls.size();
  }

  /** True if the nearest enclosing class/interface declaration of {@code node} is {@code cls}. */
  private static boolean enclosingTypeIs(Node node, ClassOrInterfaceDeclaration cls) {
    for (Optional<Node> p = node.getParentNode(); p.isPresent(); p = p.get().getParentNode()) {
      Node ancestor = p.get();
      if (ancestor instanceof ClassOrInterfaceDeclaration) {
        return ancestor == cls;
      }
    }
    return false;
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port of Python normalize() (Python-calibrated piecewise linear, Oct 2025):
    //   v <= 15        : 1.0
    //   v <= 50        : 1.0 - 0.25*(v-15)/35
    //   v <= 100       : 0.75 - 0.40*(v-50)/50
    //   v <= 120       : 0.35 - 0.35*(v-100)/20
    //   else           : 0.0
    if (value == null) {
      return 1.0;
    }
    double v = value;
    if (v <= 15.0) {
      return 1.0;
    }
    if (v <= 50.0) {
      return 1.0 - 0.25 * (v - 15.0) / 35.0;
    }
    if (v <= 100.0) {
      return 0.75 - 0.40 * (v - 50.0) / 50.0;
    }
    if (v <= 120.0) {
      return 0.35 - 0.35 * (v - 100.0) / 20.0;
    }
    return 0.0;
  }

  @Override
  public double getWeight() {
    return 0.65;
  }

  @Override
  public String getName() {
    return "rfc";
  }
}
