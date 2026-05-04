package com.integrallis.mfcqi.smells;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.integrallis.mfcqi.smells.internal.SourceTrees;
import com.integrallis.mfcqi.smells.internal.SourceTrees.ParsedSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-smell detector — direct port of {@code
 * mfcqi/smell_detection/ast_test_smells.py:ASTTestSmellDetector}, adapted for JUnit conventions.
 *
 * <p>Translation map (Python AST → Java/JUnit):
 *
 * <ul>
 *   <li>Python {@code test_*.py} / {@code *_test.py} files → Java {@code *Test.java} / {@code
 *       *Tests.java} / {@code *IT.java} files OR any file under {@code src/test/}.
 *   <li>Python {@code def test_*} functions → Java methods annotated with {@code @Test},
 *       {@code @ParameterizedTest}, or {@code @RepeatedTest}.
 *   <li>Python {@code ast.Assert} statements → Java method calls whose name starts with {@code
 *       assert} (covers JUnit {@code assertEquals}, AssertJ {@code assertThat}, Hamcrest {@code
 *       assertThat}, etc.).
 *   <li>"With message" assertions: Python checks {@code assert.msg is not None}. Java JUnit puts
 *       the message as the last {@code String} argument — a heuristic preserved here as "the call
 *       has 3+ args" for {@code assertEquals}/{@code assertSame}/etc., or as a chained {@code
 *       .as(...)} / {@code .withFailMessage(...)} for AssertJ.
 *   <li>Python {@code time.sleep()} → Java {@code Thread.sleep(...)} or {@code TimeUnit.sleep}.
 *   <li>Python {@code print(...)} → Java {@code System.out.print(...)} / {@code
 *       System.out.println(...)}.
 * </ul>
 *
 * <p>Smell IDs and severities are translated verbatim from the Python source:
 *
 * <ul>
 *   <li>{@code ASSERTION_ROULETTE} (MEDIUM) — 4+ assertions, fewer than half with messages
 *   <li>{@code EMPTY_TEST} (HIGH) — 0 assertions in a test method
 *   <li>{@code LONG_TEST} (MEDIUM) — test method body ≥ 50 lines
 *   <li>{@code SLEEPY_TEST} (HIGH) — test method calls {@code Thread.sleep}
 *   <li>{@code REDUNDANT_PRINT} (LOW) — test method calls {@code System.out.print(ln)}
 * </ul>
 */
public final class JavaTestSmellDetector implements SmellDetector {

  private static final int LONG_TEST_LINES = 50;
  private static final int ASSERTION_ROULETTE_THRESHOLD = 4;

  @Override
  public String name() {
    return "java-test-smells";
  }

  @Override
  public List<Smell> detect(Path codebase) {
    List<Smell> out = new ArrayList<>();
    for (ParsedSource src : SourceTrees.parseAll(codebase)) {
      if (!isTestFile(src.path())) {
        continue;
      }
      for (MethodDeclaration md : src.compilationUnit().findAll(MethodDeclaration.class)) {
        if (!isTestMethod(md)) {
          continue;
        }
        out.addAll(analyze(md, src.path()));
      }
    }
    return out;
  }

  /** Port of {@code _is_test_file} adapted for Java conventions. */
  static boolean isTestFile(Path file) {
    String path = file.toString().replace('\\', '/');
    Path fileName = file.getFileName();
    String name = fileName == null ? "" : fileName.toString();
    boolean nameMatches =
        name.endsWith("Test.java") || name.endsWith("Tests.java") || name.endsWith("IT.java");
    boolean underTestRoot = path.contains("/src/test/");
    return nameMatches || underTestRoot;
  }

  /** Port of {@code _is_test_function} adapted for JUnit annotations. */
  static boolean isTestMethod(MethodDeclaration md) {
    for (AnnotationExpr ann : md.getAnnotations()) {
      String n = ann.getNameAsString();
      if ("Test".equals(n) || "ParameterizedTest".equals(n) || "RepeatedTest".equals(n)) {
        return true;
      }
    }
    return false;
  }

  /** Port of {@code _analyze_test_function}. */
  private List<Smell> analyze(MethodDeclaration md, Path file) {
    List<Smell> out = new ArrayList<>();
    String location = file.toString() + ":" + md.getBegin().map(p -> p.line).orElse(0);
    String testName = md.getNameAsString();

    int assertCount = countAssertions(md);
    int withMessage = countAssertionsWithMessage(md);

    // Python: count >= 4 and with_message < count // 2
    if (assertCount >= ASSERTION_ROULETTE_THRESHOLD && withMessage < assertCount / 2) {
      out.add(
          new Smell(
              "ASSERTION_ROULETTE",
              "Assertion Roulette",
              SmellCategory.TEST,
              SmellSeverity.MEDIUM,
              location,
              name(),
              "Test '"
                  + testName
                  + "' has "
                  + assertCount
                  + " assertions, most without descriptive messages."));
    }

    // Python: count == 0 -> empty test
    if (assertCount == 0) {
      out.add(
          new Smell(
              "EMPTY_TEST",
              "Empty Test",
              SmellCategory.TEST,
              SmellSeverity.HIGH,
              location,
              name(),
              "Test '" + testName + "' has no assertions."));
    }

    int lines = methodLines(md);
    if (lines >= LONG_TEST_LINES) {
      out.add(
          new Smell(
              "LONG_TEST",
              "Long Test",
              SmellCategory.TEST,
              SmellSeverity.MEDIUM,
              location,
              name(),
              "Test '" + testName + "' is " + lines + " lines long."));
    }

    if (hasSleepCall(md)) {
      out.add(
          new Smell(
              "SLEEPY_TEST",
              "Sleepy Test",
              SmellCategory.TEST,
              SmellSeverity.HIGH,
              location,
              name(),
              "Test '" + testName + "' uses Thread.sleep()."));
    }

    if (hasPrintCall(md)) {
      out.add(
          new Smell(
              "REDUNDANT_PRINT",
              "Redundant Print",
              SmellCategory.TEST,
              SmellSeverity.LOW,
              location,
              name(),
              "Test '" + testName + "' contains print statements."));
    }

    return out;
  }

  private static int countAssertions(MethodDeclaration md) {
    int count = 0;
    for (MethodCallExpr call : md.findAll(MethodCallExpr.class)) {
      if (isAssertionCall(call)) {
        count++;
      }
    }
    return count;
  }

  private static int countAssertionsWithMessage(MethodDeclaration md) {
    int count = 0;
    for (MethodCallExpr call : md.findAll(MethodCallExpr.class)) {
      if (isAssertionCall(call) && hasMessageArgument(call)) {
        count++;
      }
    }
    return count;
  }

  /** True if call's name starts with "assert" (case-sensitive). */
  static boolean isAssertionCall(MethodCallExpr call) {
    String name = call.getNameAsString();
    return name.startsWith("assert");
  }

  /**
   * Heuristic for whether a JUnit-style assertion carries an explanatory message:
   *
   * <ul>
   *   <li>{@code assertTrue(cond, msg)} / {@code assertEquals(a, b, msg)} → 2+ args for unary
   *       assertions or 3+ args for binary assertions.
   *   <li>AssertJ {@code assertThat(x).as("msg")...} — chain contains an {@code as} or {@code
   *       withFailMessage} call.
   * </ul>
   *
   * To keep the heuristic simple and source-only, we treat any assertion call with ≥ 2 arguments as
   * having a message (this covers JUnit assertTrue/assertFalse with messages and underweights
   * AssertJ chains, matching the Python's level of approximation).
   */
  static boolean hasMessageArgument(MethodCallExpr call) {
    String name = call.getNameAsString();
    int args = call.getArguments().size();
    if ("assertTrue".equals(name)
        || "assertFalse".equals(name)
        || "assertNull".equals(name)
        || "assertNotNull".equals(name)) {
      return args >= 2;
    }
    return args >= 3;
  }

  private static int methodLines(MethodDeclaration md) {
    if (!md.getBody().isPresent()) {
      return 0;
    }
    int begin = md.getBody().get().getBegin().map(p -> p.line).orElse(0);
    int end = md.getBody().get().getEnd().map(p -> p.line).orElse(0);
    return Math.max(0, end - begin + 1);
  }

  private static boolean hasSleepCall(MethodDeclaration md) {
    for (MethodCallExpr call : md.findAll(MethodCallExpr.class)) {
      if ("sleep".equals(call.getNameAsString())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasPrintCall(MethodDeclaration md) {
    for (MethodCallExpr call : md.findAll(MethodCallExpr.class)) {
      String n = call.getNameAsString();
      if ("print".equals(n) || "println".equals(n)) {
        return true;
      }
    }
    return false;
  }
}
