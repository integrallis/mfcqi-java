package com.integrallis.mfcqi.metrics.internal;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers the executable units in a parsed file that are subject to cyclomatic-complexity
 * counting: method bodies, constructor bodies, and static/instance initializer blocks. Mirrors the
 * role {@code radon.complexity.cc_visit} plays for the Python reference, which yields one
 * complexity entry per {@code FunctionDef} / {@code AsyncFunctionDef} (and class).
 */
public final class CyclomaticUnits {

  private CyclomaticUnits() {}

  /** Compute a CC value per executable unit in {@code file}. Returns an empty list if none. */
  public static List<Integer> complexitiesIn(ParsedFile file) {
    List<Integer> out = new ArrayList<>();
    for (MethodDeclaration m : file.compilationUnit().findAll(MethodDeclaration.class)) {
      if (m.getBody().isPresent()) {
        out.add(CyclomaticVisitor.complexityOf(m));
      }
    }
    for (ConstructorDeclaration c : file.compilationUnit().findAll(ConstructorDeclaration.class)) {
      out.add(CyclomaticVisitor.complexityOf(c));
    }
    for (InitializerDeclaration i : file.compilationUnit().findAll(InitializerDeclaration.class)) {
      out.add(CyclomaticVisitor.complexityOf(i));
    }
    return out;
  }
}
