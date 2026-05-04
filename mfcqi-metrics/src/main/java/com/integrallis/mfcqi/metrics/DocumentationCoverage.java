package com.integrallis.mfcqi.metrics;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.integrallis.mfcqi.core.Metric;
import com.integrallis.mfcqi.metrics.internal.ParsedFile;
import com.integrallis.mfcqi.metrics.internal.ParsedSources;
import java.nio.file.Path;
import java.util.List;

/**
 * Documentation Coverage — percentage of "documentable" units that carry a Javadoc comment.
 *
 * <p>Direct port of {@code mfcqi/metrics/documentation.py:DocumentationCoverage}. The Python
 * implementation walks every file's AST and counts:
 *
 * <ul>
 *   <li>{@code +1} per module (with {@code +1} documented if the module has a docstring)
 *   <li>{@code +1} per public function/class (name not starting with {@code _}), with {@code +1}
 *       documented if the node has a docstring
 * </ul>
 *
 * <p>Java equivalence:
 *
 * <ul>
 *   <li><b>Module docstring</b> → top-level type's Javadoc, or for {@code package-info.java} the
 *       package declaration's Javadoc. Each file contributes {@code +1 documentable}.
 *   <li><b>Public definition</b> → Java {@code public} modifier on classes/interfaces/enums/
 *       annotations and on methods/constructors. Python's {@code not name.startswith("_")} test
 *       maps to Java's {@code public} access modifier — the strictest reasonable analog.
 * </ul>
 *
 * <p>Normalization, weight, and name are translated verbatim from the Python source.
 */
public final class DocumentationCoverage extends Metric<Double> {

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    List<ParsedFile> files = ParsedSources.parseAll(codebase);
    if (files.isEmpty()) {
      // Mirrors Python: `if not py_files: return 0.0`.
      return 0.0;
    }
    int totalDocumentable = 0;
    int totalDocumented = 0;
    for (ParsedFile file : files) {
      FileStats s = analyzeFileDocumentation(file);
      totalDocumentable += s.documentable;
      totalDocumented += s.documented;
    }
    if (totalDocumentable == 0) {
      return 0.0;
    }
    return (((double) totalDocumented) / totalDocumentable) * 100.0;
  }

  /** Port of Python's {@code _analyze_file_documentation}. */
  private static FileStats analyzeFileDocumentation(ParsedFile file) {
    int documentable = 0;
    int documented = 0;

    // Module-level docstring equivalent: the package declaration's Javadoc (set by
    // package-info.java) OR the first top-level type's Javadoc. Every file is +1 documentable
    // — matches Python's "Every module should have a docstring".
    documentable += 1;
    if (hasModuleDoc(file)) {
      documented += 1;
    }

    // Public functions and classes — port of `for node in ast.walk(tree): if
    // _is_public_definition`.
    for (TypeDeclaration<?> type : file.compilationUnit().findAll(TypeDeclaration.class)) {
      if (isPublicType(type)) {
        documentable += 1;
        if (hasJavadoc(type)) {
          documented += 1;
        }
      }
    }
    for (MethodDeclaration md : file.compilationUnit().findAll(MethodDeclaration.class)) {
      if (md.isPublic()) {
        documentable += 1;
        if (hasJavadoc(md)) {
          documented += 1;
        }
      }
    }
    for (ConstructorDeclaration cd : file.compilationUnit().findAll(ConstructorDeclaration.class)) {
      if (cd.isPublic()) {
        documentable += 1;
        if (hasJavadoc(cd)) {
          documented += 1;
        }
      }
    }
    return new FileStats(documentable, documented);
  }

  private static boolean isPublicType(TypeDeclaration<?> type) {
    if (type instanceof ClassOrInterfaceDeclaration) {
      return ((ClassOrInterfaceDeclaration) type).isPublic();
    }
    if (type instanceof EnumDeclaration) {
      return ((EnumDeclaration) type).isPublic();
    }
    if (type instanceof AnnotationDeclaration) {
      return ((AnnotationDeclaration) type).isPublic();
    }
    return false;
  }

  private static boolean hasJavadoc(NodeWithJavadoc<?> node) {
    return node.getJavadocComment().isPresent();
  }

  private static boolean hasModuleDoc(ParsedFile file) {
    // PackageDeclaration in JavaParser does NOT implement NodeWithJavadoc, but a leading Javadoc
    // attaches via the generic Comment API. CompilationUnit may also own a leading comment.
    if (file.compilationUnit().getPackageDeclaration().isPresent()
        && file.compilationUnit().getPackageDeclaration().get().getComment().isPresent()) {
      return true;
    }
    if (file.compilationUnit().getComment().isPresent()) {
      return true;
    }
    // Fallback for files whose Javadoc lands on neither the package nor the CU: any Javadoc-style
    // block comment in the file is enough to count the module as documented.
    if (file.compilationUnit().getAllContainedComments().stream()
        .anyMatch(com.github.javaparser.ast.comments.Comment::isJavadocComment)) {
      return true;
    }
    // Otherwise, treat the first top-level type's Javadoc as the module-level documentation.
    return file.compilationUnit().getTypes().stream()
        .findFirst()
        .map(DocumentationCoverage::hasJavadoc)
        .orElse(false);
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port of Python normalize():
    //   if v <= 0   : 0.0
    //   if v >= 100 : 1.0
    //   else        : v / 100.0
    if (value == null) {
      return 0.0;
    }
    double v = value;
    if (v <= 0.0) {
      return 0.0;
    }
    if (v >= 100.0) {
      return 1.0;
    }
    return v / 100.0;
  }

  @Override
  public double getWeight() {
    return 0.4;
  }

  @Override
  public String getName() {
    return "Documentation Coverage";
  }

  private static final class FileStats {
    final int documentable;
    final int documented;

    FileStats(int documentable, int documented) {
      this.documentable = documentable;
      this.documented = documented;
    }
  }
}
