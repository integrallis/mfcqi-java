package com.integrallis.mfcqi.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.integrallis.mfcqi.core.Metric;
import com.integrallis.mfcqi.metrics.internal.ParsedFile;
import com.integrallis.mfcqi.metrics.internal.ParsedSources;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lack of Cohesion of Methods (LCOM4) — number of connected components in the method-attribute
 * graph, averaged across classes.
 *
 * <p>Direct port of {@code mfcqi/metrics/cohesion.py:LackOfCohesionOfMethods}. The Python
 * implementation:
 *
 * <ul>
 *   <li>Skips magic methods (names starting with {@code __}) — Python's analog of constructors and
 *       protocol methods. The Java port skips {@code <init>}-equivalent constructors only; methods
 *       that override {@code Object} (e.g., {@code toString}, {@code equals}) are retained because
 *       they are normal Java methods.
 *   <li>For each remaining method, collects the set of instance variables it touches via {@code
 *       self.attr}. Java equivalent: {@link FieldAccessExpr} with a {@link ThisExpr} scope AND
 *       unqualified {@link NameExpr} whose name matches a declared field of the class (Java
 *       idiomatically omits the {@code this.} qualifier).
 *   <li>Builds a graph where two methods are connected iff they share at least one instance
 *       variable.
 *   <li>LCOM4 = number of connected components (1 = perfectly cohesive).
 * </ul>
 *
 * <p>Returns the average LCOM across all classes (matches Python's behavior).
 */
public final class LackOfCohesionOfMethods extends Metric<Double> {

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    List<ParsedFile> files = ParsedSources.parseAll(codebase);
    double sum = 0.0;
    int count = 0;
    for (ParsedFile file : files) {
      for (ClassOrInterfaceDeclaration cls :
          file.compilationUnit().findAll(ClassOrInterfaceDeclaration.class)) {
        sum += lcomForClass(cls);
        count++;
      }
    }
    return count == 0 ? 0.0 : sum / count;
  }

  private static double lcomForClass(ClassOrInterfaceDeclaration cls) {
    Set<String> declaredFields = new HashSet<>();
    for (FieldDeclaration fd : cls.getFields()) {
      for (VariableDeclarator vd : fd.getVariables()) {
        declaredFields.add(vd.getNameAsString());
      }
    }

    List<MethodEntry> methods = new ArrayList<>();
    for (MethodDeclaration md : cls.getMethods()) {
      // Python skips `__name__` magic methods. Java analog: skip constructors (handled separately
      // — they're ConstructorDeclaration, not MethodDeclaration). All other methods retained.
      Set<String> touched = methodInstanceVars(md, declaredFields);
      methods.add(new MethodEntry(md.getNameAsString(), touched));
    }

    if (methods.size() <= 1) {
      // Python: single method or none = perfect cohesion (returns 0.0)
      return 0.0;
    }

    Map<String, Set<String>> connections = buildMethodConnections(methods);
    int components = countConnectedComponents(connections, methods);
    return components;
  }

  /** Port of {@code _get_method_instance_vars}. */
  private static Set<String> methodInstanceVars(MethodDeclaration md, Set<String> declaredFields) {
    Set<String> touched = new HashSet<>();
    // Python: `self.var_name` -> add var_name. Java equivalent: `this.field`.
    for (FieldAccessExpr fa : md.findAll(FieldAccessExpr.class)) {
      Node scope = fa.getScope();
      if (scope instanceof ThisExpr) {
        touched.add(fa.getNameAsString());
      }
    }
    // Java idiom: bare `field` (NameExpr) when not shadowed by a local. We can't always know
    // shadowing without symbol resolution, so we conservatively count any NameExpr that matches a
    // declared field name. This adds Java-relevant signal beyond `this.`-only access; the Python
    // port has no equivalent because Python always requires `self.`.
    for (NameExpr ne : md.findAll(NameExpr.class)) {
      String name = ne.getNameAsString();
      if (declaredFields.contains(name)) {
        touched.add(name);
      }
    }
    return touched;
  }

  /** Port of {@code _build_method_connections}. */
  private static Map<String, Set<String>> buildMethodConnections(List<MethodEntry> methods) {
    Map<String, Set<String>> connections = new HashMap<>();
    for (int i = 0; i < methods.size(); i++) {
      for (int j = 0; j < methods.size(); j++) {
        if (i == j) {
          continue;
        }
        if (sharesAny(methods.get(i).vars, methods.get(j).vars)) {
          connections
              .computeIfAbsent(methods.get(i).name, k -> new HashSet<>())
              .add(methods.get(j).name);
        }
      }
    }
    return connections;
  }

  private static boolean sharesAny(Set<String> a, Set<String> b) {
    Set<String> small = a.size() <= b.size() ? a : b;
    Set<String> big = a.size() <= b.size() ? b : a;
    for (String s : small) {
      if (big.contains(s)) {
        return true;
      }
    }
    return false;
  }

  /** Port of {@code _count_connected_components}. */
  private static int countConnectedComponents(
      Map<String, Set<String>> connections, List<MethodEntry> methods) {
    if (connections.isEmpty()) {
      // Python: no connections means one component.
      return 1;
    }
    Set<String> allNames = new HashSet<>();
    for (MethodEntry m : methods) {
      allNames.add(m.name);
    }
    allNames.addAll(connections.keySet());
    for (Set<String> neighbors : connections.values()) {
      allNames.addAll(neighbors);
    }

    Set<String> visited = new HashSet<>();
    int components = 0;
    for (String name : allNames) {
      if (!visited.contains(name)) {
        dfs(name, connections, visited);
        components++;
      }
    }
    return Math.max(1, components);
  }

  private static void dfs(String start, Map<String, Set<String>> connections, Set<String> visited) {
    java.util.Deque<String> stack = new java.util.ArrayDeque<>();
    stack.push(start);
    while (!stack.isEmpty()) {
      String node = stack.pop();
      if (!visited.add(node)) {
        continue;
      }
      Set<String> neighbors = connections.getOrDefault(node, Collections.emptySet());
      for (String n : neighbors) {
        if (!visited.contains(n)) {
          stack.push(n);
        }
      }
    }
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port of Python normalize():
    //   v <= 1  : 1.0
    //   v <= 3  : 1.0 - ((v-1)/2) * 0.3
    //   v <= 6  : 0.7 - ((v-3)/3) * 0.3
    //   else    : max(0, 0.4 - ((v-6)/6) * 0.4)
    if (value == null) {
      return 1.0;
    }
    double v = value;
    if (v <= 1.0) {
      return 1.0;
    }
    if (v <= 3.0) {
      return 1.0 - ((v - 1.0) / 2.0) * 0.3;
    }
    if (v <= 6.0) {
      return 0.7 - ((v - 3.0) / 3.0) * 0.3;
    }
    return Math.max(0.0, 0.4 - ((v - 6.0) / 6.0) * 0.4);
  }

  @Override
  public double getWeight() {
    return 0.50;
  }

  @Override
  public String getName() {
    return "Lack of Cohesion of Methods";
  }

  private static final class MethodEntry {
    final String name;
    final Set<String> vars;

    MethodEntry(String name, Set<String> vars) {
      this.name = name;
      this.vars = vars;
    }
  }
}
