package com.integrallis.mfcqi.metrics.internal;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Counts McCabe cyclomatic complexity within a single executable unit (method, constructor, or
 * initializer block). Each unit starts at complexity 1 and increments per decision point.
 *
 * <p>Decision points (matches the standard McCabe set used by SonarQube, JaCoCo, PMD, and the
 * Python radon library that backs {@code mfcqi/metrics/complexity.py:CyclomaticComplexity}):
 *
 * <ul>
 *   <li>{@code if} (each {@code else if} is itself a nested {@code if} in the JavaParser AST)
 *   <li>{@code for}, {@code foreach}, {@code while}, {@code do-while}
 *   <li>each {@code case} label of a {@code switch} (default does not count)
 *   <li>each {@code catch} clause
 *   <li>each {@code &&} and {@code ||} in a boolean expression
 *   <li>each {@code ?:} ternary
 * </ul>
 */
final class CyclomaticVisitor extends VoidVisitorAdapter<int[]> {

  /** Compute CC for a single executable unit. */
  static int complexityOf(Node unit) {
    int[] count = {1};
    new CyclomaticVisitor().visit(unit, count);
    return count[0];
  }

  /** Public entry — kept for the {@link Node} subtypes we care about. */
  void visit(Node node, int[] count) {
    if (node instanceof MethodDeclaration) {
      super.visit((MethodDeclaration) node, count);
    } else if (node instanceof ConstructorDeclaration) {
      super.visit((ConstructorDeclaration) node, count);
    } else if (node instanceof InitializerDeclaration) {
      super.visit((InitializerDeclaration) node, count);
    } else {
      // Generic fallback — walk children explicitly.
      for (Node child : node.getChildNodes()) {
        child.accept(this, count);
      }
    }
  }

  @Override
  public void visit(IfStmt n, int[] count) {
    count[0]++;
    super.visit(n, count);
  }

  @Override
  public void visit(ForStmt n, int[] count) {
    count[0]++;
    super.visit(n, count);
  }

  @Override
  public void visit(ForEachStmt n, int[] count) {
    count[0]++;
    super.visit(n, count);
  }

  @Override
  public void visit(WhileStmt n, int[] count) {
    count[0]++;
    super.visit(n, count);
  }

  @Override
  public void visit(DoStmt n, int[] count) {
    count[0]++;
    super.visit(n, count);
  }

  @Override
  public void visit(SwitchEntry n, int[] count) {
    // One increment per case label. Default labels (empty label list) do not count.
    if (!n.getLabels().isEmpty()) {
      count[0] += n.getLabels().size();
    }
    super.visit(n, count);
  }

  @Override
  public void visit(CatchClause n, int[] count) {
    count[0]++;
    super.visit(n, count);
  }

  @Override
  public void visit(BinaryExpr n, int[] count) {
    BinaryExpr.Operator op = n.getOperator();
    if (op == BinaryExpr.Operator.AND || op == BinaryExpr.Operator.OR) {
      count[0]++;
    }
    super.visit(n, count);
  }

  @Override
  public void visit(ConditionalExpr n, int[] count) {
    count[0]++;
    super.visit(n, count);
  }
}
