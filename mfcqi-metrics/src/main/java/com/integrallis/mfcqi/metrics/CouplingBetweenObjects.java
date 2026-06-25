package com.integrallis.mfcqi.metrics;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.integrallis.mfcqi.core.Metric;
import com.integrallis.mfcqi.metrics.internal.ParsedFile;
import com.integrallis.mfcqi.metrics.internal.ParsedSources;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Coupling Between Objects (CBO) — average across classes of the count of distinct external types
 * each class references.
 *
 * <p>Direct port of {@code mfcqi/metrics/coupling.py:CouplingBetweenObjects}. The Python
 * implementation collects coupling targets from four sources and unions them, then filters out
 * built-ins and noise:
 *
 * <ul>
 *   <li>method coupling — return / parameter type annotations
 *   <li>annotation coupling — variable annotations
 *   <li>usage coupling — attribute access ({@code obj.attr}) and call sites ({@code Foo()}, {@code
 *       obj.method()})
 *   <li>inheritance coupling — base classes
 * </ul>
 *
 * <p>Java equivalence: type annotations → declared {@link Type} on parameters/return/locals/
 * fields; attribute access → {@link FieldAccessExpr} scope name; call sites → {@link
 * MethodCallExpr} scope name and unqualified {@link NameExpr} call targets; inheritance → both
 * {@code extends} and {@code implements}. The built-in / noise filters are translated to Java
 * primitive box types and the Java built-in noise list ({@code String}, {@code Object}, …).
 */
public final class CouplingBetweenObjects extends Metric<Double> {

  // Python EXCLUDED built-ins (port + Java boxed equivalents). The Python list contains lower-case
  // built-ins; Java's are capitalized box types and primitives.
  private static final Set<String> BUILTIN_TYPES;
  // Python EXCLUDED noise (self, cls, super, common builtins). Java equivalent (this, super,
  // common stdlib).
  private static final Set<String> NOISE_NAMES;

  static {
    Set<String> builtins = new HashSet<>();
    Collections.addAll(
        builtins,
        // Java primitives:
        "boolean",
        "byte",
        "short",
        "int",
        "long",
        "float",
        "double",
        "char",
        "void",
        // Boxed primitives:
        "Boolean",
        "Byte",
        "Short",
        "Integer",
        "Long",
        "Float",
        "Double",
        "Character",
        "Void",
        // Common Object analogs:
        "Object",
        "String",
        "CharSequence",
        // Common collection interfaces (mirror Python's exclusion of list/dict/set/tuple):
        "List",
        "Map",
        "Set",
        "Collection",
        "Iterable",
        // Misc:
        "Number",
        "Throwable",
        "Exception",
        "RuntimeException",
        "Error");
    BUILTIN_TYPES = Collections.unmodifiableSet(builtins);

    Set<String> noise = new HashSet<>();
    Collections.addAll(
        noise,
        "this",
        "super",
        // Common Java identifiers that surface as Name targets but don't represent coupling:
        "System",
        "Math",
        "Arrays",
        "Collections",
        "Objects",
        "Optional");
    NOISE_NAMES = Collections.unmodifiableSet(noise);
  }

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
      Set<String> jdkTypes = jdkImportedSimpleNames(file.compilationUnit());
      for (ClassOrInterfaceDeclaration cls :
          file.compilationUnit().findAll(ClassOrInterfaceDeclaration.class)) {
        sum += cboForClass(cls, jdkTypes);
        count++;
      }
    }
    return count == 0 ? 0.0 : sum / count;
  }

  private static double cboForClass(ClassOrInterfaceDeclaration cls, Set<String> jdkTypes) {
    Set<String> coupled = new HashSet<>();
    coupled.addAll(methodCoupling(cls));
    coupled.addAll(annotationCoupling(cls));
    coupled.addAll(usageCoupling(cls));
    coupled.addAll(inheritanceCoupling(cls));

    String selfName = cls.getNameAsString();
    Set<String> filtered = new HashSet<>();
    for (String s : coupled) {
      // Exclude self, noise, language built-ins, AND JDK/library types imported from
      // java.*/javax.*/jakarta.*. The Python reference effectively counts only project-level
      // couplings (its stdlib entry points fall in its builtin/noise lists); a statically-typed
      // Java signature names a JDK type (Path, Logger, Stream, IOException, …) on nearly every
      // member, so without this exclusion CBO is inflated far above the Python-equivalent value.
      if (!s.equals(selfName)
          && isMeaningfulCoupling(s)
          && isExternalType(s)
          && !jdkTypes.contains(s)) {
        filtered.add(s);
      }
    }
    return filtered.size();
  }

  /** Simple names imported from the JDK / Jakarta namespaces — treated as non-project couplings. */
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

  /** Port of {@code _get_method_coupling}. */
  private static Set<String> methodCoupling(ClassOrInterfaceDeclaration cls) {
    Set<String> coupled = new HashSet<>();
    for (MethodDeclaration md : cls.findAll(MethodDeclaration.class)) {
      addType(md.getType(), coupled);
      for (Parameter p : md.getParameters()) {
        addType(p.getType(), coupled);
      }
    }
    return coupled;
  }

  /** Port of {@code _get_annotation_coupling}. */
  private static Set<String> annotationCoupling(ClassOrInterfaceDeclaration cls) {
    Set<String> coupled = new HashSet<>();
    for (VariableDeclarator vd : cls.findAll(VariableDeclarator.class)) {
      addType(vd.getType(), coupled);
    }
    return coupled;
  }

  /** Port of {@code _get_usage_coupling}. */
  private static Set<String> usageCoupling(ClassOrInterfaceDeclaration cls) {
    Set<String> coupled = new HashSet<>();
    for (FieldAccessExpr fa : cls.findAll(FieldAccessExpr.class)) {
      // Python: `obj.attr` — collect `obj`. Java FieldAccessExpr scope is the qualifier.
      Node scope = fa.getScope();
      if (scope instanceof NameExpr) {
        coupled.add(((NameExpr) scope).getNameAsString());
      }
    }
    for (MethodCallExpr call : cls.findAll(MethodCallExpr.class)) {
      // Only qualified calls `obj.method()` couple to `obj`. An unqualified `foo()` in Java is a
      // call to a method of this class (or a static import) — a local/inherited method, not an
      // external coupling — so unlike the Python port we do not add the bare call name.
      if (call.getScope().isPresent()) {
        Node scope = call.getScope().get();
        if (scope instanceof NameExpr) {
          coupled.add(((NameExpr) scope).getNameAsString());
        }
      }
    }
    return coupled;
  }

  /** Port of {@code _get_inheritance_coupling}. Includes implements (Java-specific). */
  private static Set<String> inheritanceCoupling(ClassOrInterfaceDeclaration cls) {
    Set<String> coupled = new HashSet<>();
    for (ClassOrInterfaceType base : cls.getExtendedTypes()) {
      coupled.add(base.getNameAsString());
    }
    for (ClassOrInterfaceType iface : cls.getImplementedTypes()) {
      coupled.add(iface.getNameAsString());
    }
    return coupled;
  }

  /** Port of {@code _extract_type_name} via JavaParser's resolved name. */
  private static void addType(Type type, Set<String> out) {
    if (type instanceof ClassOrInterfaceType) {
      out.add(((ClassOrInterfaceType) type).getNameAsString());
    }
  }

  /** Port of {@code _is_external_type} — exclude built-ins / language-primitive types. */
  private static boolean isExternalType(String typeName) {
    return !BUILTIN_TYPES.contains(typeName);
  }

  /** Port of {@code _is_meaningful_coupling} — exclude noise + tiny names. */
  private static boolean isMeaningfulCoupling(String name) {
    return !NOISE_NAMES.contains(name) && name.length() > 1;
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port of Python normalize():
    //   v <= 0  : 1.0
    //   v <= 9  : 1.0 - (v/9) * 0.3
    //   v <= 20 : 0.7 - ((v-9)/11) * 0.3
    //   else    : max(0, 0.4 - ((v-20)/20) * 0.4)
    if (value == null) {
      return 1.0;
    }
    double v = value;
    if (v <= 0.0) {
      return 1.0;
    }
    if (v <= 9.0) {
      return 1.0 - (v / 9.0) * 0.3;
    }
    if (v <= 20.0) {
      return 0.7 - ((v - 9.0) / 11.0) * 0.3;
    }
    return Math.max(0.0, 0.4 - ((v - 20.0) / 20.0) * 0.4);
  }

  @Override
  public double getWeight() {
    return 0.65;
  }

  @Override
  public String getName() {
    return "Coupling Between Objects";
  }
}
