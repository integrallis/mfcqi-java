package com.integrallis.mfcqi.metrics.internal;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;

/**
 * Cognitive Complexity counter — direct port of the Python {@code cognitive_complexity} library
 * ({@code cognitive_complexity/api.py} and {@code cognitive_complexity/utils/ast.py}) which
 * implements Campbell's 2018 algorithm.
 *
 * <p>Per-node mapping (Python AST → JavaParser AST):
 *
 * <ul>
 *   <li>{@code ast.If} → {@link IfStmt} (with the elif chain detection translated literally: Python
 *       checks {@code len(node.orelse) == 1 and orelse[0] is If}; the Java equivalent is "elseStmt
 *       is itself an IfStmt")
 *   <li>{@code ast.IfExp} (ternary) → {@link ConditionalExpr}
 *   <li>{@code ast.For} / {@code ast.While} → {@link ForStmt}, {@link ForEachStmt}, {@link
 *       WhileStmt}, {@link DoStmt}
 *   <li>{@code ast.ExceptHandler} → {@link CatchClause}
 *   <li>{@code ast.FunctionDef}/{@code ast.Lambda} → {@link MethodDeclaration}, {@link
 *       ConstructorDeclaration}, {@link InitializerDeclaration}, {@link LambdaExpr}
 *   <li>{@code ast.BoolOp} → {@link BinaryExpr} with {@code &&}/{@code ||}
 * </ul>
 *
 * <p>For boolean expressions: Python parses {@code a and b and c} as one {@code BoolOp(And,
 * [a,b,c])} node and the library counts {@code len([n for n in ast.walk(node) if BoolOp])} —
 * effectively, one count per maximal same-operator sequence plus one per operator change. Java
 * parses {@code a && b && c} as nested {@code BinaryExpr} nodes; this implementation produces the
 * same count by walking the boolean subtree and incrementing on each operator-change boundary (the
 * topmost boolean node also counts).
 */
public final class CognitiveVisitor {

  private CognitiveVisitor() {}

  /** Cognitive complexity of one method/constructor/initializer body. */
  public static int complexityOf(CallableDeclaration<?> callable) {
    int complexity = 0;
    BlockStmt body = bodyOf(callable);
    if (body != null) {
      for (Statement stmt : body.getStatements()) {
        complexity += complexityForNode(stmt, 0);
      }
    }
    if (hasRecursiveCalls(callable)) {
      // Python: `if has_recursive_calls(funcdef): complexity += 1`
      complexity += 1;
    }
    return complexity;
  }

  /** Cognitive complexity of a static/instance initializer block. */
  public static int complexityOf(InitializerDeclaration init) {
    int complexity = 0;
    for (Statement stmt : init.getBody().getStatements()) {
      complexity += complexityForNode(stmt, 0);
    }
    return complexity;
  }

  private static BlockStmt bodyOf(CallableDeclaration<?> callable) {
    if (callable instanceof MethodDeclaration) {
      return ((MethodDeclaration) callable).getBody().orElse(null);
    }
    if (callable instanceof ConstructorDeclaration) {
      return ((ConstructorDeclaration) callable).getBody();
    }
    return null;
  }

  /** Recursive descent over the AST. Direct port of {@code get_cognitive_complexity_for_node}. */
  static int complexityForNode(Node node, int incrementBy) {
    NodeProcessing p = processNodeItself(node, incrementBy);
    int childComplexity = 0;
    if (p.shouldIterChildren) {
      for (Node child : node.getChildNodes()) {
        childComplexity += complexityForNode(child, p.incrementBy);
      }
    }
    return p.baseComplexity + childComplexity;
  }

  /** Direct port of {@code process_node_itself}. */
  private static NodeProcessing processNodeItself(Node node, int incrementBy) {
    if (node instanceof IfStmt) {
      return processIf((IfStmt) node, incrementBy);
    }
    if (node instanceof ConditionalExpr) {
      // Python ast.IfExp: increment=0, increment_by += 1, base = max(1, increment_by)
      int newInc = incrementBy + 1;
      return new NodeProcessing(newInc, Math.max(1, newInc), true);
    }
    if (node instanceof CatchClause) {
      // Python ast.ExceptHandler
      int newInc = incrementBy + 1;
      return new NodeProcessing(newInc, Math.max(1, newInc), true);
    }
    if (node instanceof ForStmt
        || node instanceof ForEachStmt
        || node instanceof WhileStmt
        || node instanceof DoStmt) {
      // Python ast.For/ast.While: no orelse handled here; just nesting
      int newInc = incrementBy + 1;
      return new NodeProcessing(newInc, Math.max(1, newInc), true);
    }
    if (node instanceof MethodDeclaration
        || node instanceof ConstructorDeclaration
        || node instanceof InitializerDeclaration
        || node instanceof LambdaExpr) {
      // Python "incrementers_nodes": increment_by += 1, base = 0
      return new NodeProcessing(incrementBy + 1, 0, true);
    }
    if (node instanceof BinaryExpr) {
      BinaryExpr be = (BinaryExpr) node;
      BinaryExpr.Operator op = be.getOperator();
      if (op == BinaryExpr.Operator.AND || op == BinaryExpr.Operator.OR) {
        // Python: count(BoolOps in subtree); should_iter_children = False.
        // To avoid double-counting nested same-operator chains, only the topmost boolean node in
        // a same-operator chain triggers counting; nested same-op nodes are handled by their
        // ancestor's walk.
        if (isInsideSameBoolChain(be)) {
          return new NodeProcessing(incrementBy, 0, false);
        }
        int chunks = countBoolChunks(be, null);
        return new NodeProcessing(incrementBy, chunks, false);
      }
    }
    // Default: pass through, iter children with same increment_by, no base complexity.
    return new NodeProcessing(incrementBy, 0, true);
  }

  /** Direct port of {@code process_control_flow_breaker} for {@link IfStmt}. */
  private static NodeProcessing processIf(IfStmt ifStmt, int incrementBy) {
    boolean hasElse = ifStmt.getElseStmt().isPresent();
    boolean isElifChain = hasElse && ifStmt.getElseStmt().get() instanceof IfStmt;

    int increment;
    int newIncrementBy;
    if (isElifChain) {
      // Python: "node is an elif; the increment will be counted on the ast.If" — no extra +1, no
      // nesting bump. The chained inner IfStmt picks up its own contribution when walked.
      increment = 0;
      newIncrementBy = incrementBy;
    } else if (hasElse) {
      // Python "elif node.orelse" branch: +1 for the else, plus a nesting level.
      increment = 1;
      newIncrementBy = incrementBy + 1;
    } else {
      // Python "no else, just add a nesting level"
      increment = 0;
      newIncrementBy = incrementBy + 1;
    }
    int base = Math.max(1, newIncrementBy) + increment;
    return new NodeProcessing(newIncrementBy, base, true);
  }

  /**
   * True if {@code expr} is the direct operand of a {@link BinaryExpr} with the same {@code
   * &&}/{@code ||} operator — meaning it is a continuation of a chain that is already being counted
   * by an ancestor.
   */
  private static boolean isInsideSameBoolChain(BinaryExpr expr) {
    Node parent = expr.getParentNode().orElse(null);
    if (!(parent instanceof BinaryExpr)) {
      return false;
    }
    BinaryExpr.Operator parentOp = ((BinaryExpr) parent).getOperator();
    return parentOp == expr.getOperator()
        && (parentOp == BinaryExpr.Operator.AND || parentOp == BinaryExpr.Operator.OR);
  }

  /**
   * Count the number of "boolean operator chunks" in an expression tree: each maximal same-op
   * subsequence is one chunk, mirroring Python's {@code BoolOp} node count.
   */
  private static int countBoolChunks(Expression expr, BinaryExpr.Operator parentOp) {
    if (!(expr instanceof BinaryExpr)) {
      return 0;
    }
    BinaryExpr be = (BinaryExpr) expr;
    BinaryExpr.Operator op = be.getOperator();
    if (op != BinaryExpr.Operator.AND && op != BinaryExpr.Operator.OR) {
      return 0;
    }
    int count = (op != parentOp) ? 1 : 0;
    count += countBoolChunks(be.getLeft(), op);
    count += countBoolChunks(be.getRight(), op);
    return count;
  }

  /** Direct port of {@code has_recursive_calls}. Name-only, no symbol resolution. */
  private static boolean hasRecursiveCalls(CallableDeclaration<?> callable) {
    if (!(callable instanceof MethodDeclaration)) {
      return false;
    }
    String name = callable.getNameAsString();
    return callable.findAll(MethodCallExpr.class).stream()
        .anyMatch(call -> call.getNameAsString().equals(name));
  }

  /** Tuple type for {@link #processNodeItself}'s three-value return. */
  private static final class NodeProcessing {
    final int incrementBy;
    final int baseComplexity;
    final boolean shouldIterChildren;

    NodeProcessing(int incrementBy, int baseComplexity, boolean shouldIterChildren) {
      this.incrementBy = incrementBy;
      this.baseComplexity = baseComplexity;
      this.shouldIterChildren = shouldIterChildren;
    }
  }
}
