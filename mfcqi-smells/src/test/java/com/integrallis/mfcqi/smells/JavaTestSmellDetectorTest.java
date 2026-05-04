package com.integrallis.mfcqi.smells;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class JavaTestSmellDetectorTest {

  private final JavaTestSmellDetector detector = new JavaTestSmellDetector();

  @Test
  void name_isJavaTestSmells() {
    assertThat(detector.name()).isEqualTo("java-test-smells");
  }

  @Test
  void detect_emptyTestFlaggedAsHigh(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/test/java"));
    Files.writeString(
        src.resolve("FooTest.java"),
        "import org.junit.jupiter.api.Test;\n"
            + "public class FooTest {\n"
            + "  @Test\n"
            + "  void doesNothing() {\n"
            + "    int x = 1;\n"
            + "    int y = 2;\n"
            + "  }\n"
            + "}");
    List<Smell> smells = detector.detect(tmp);
    assertThat(smells).extracting(Smell::id).contains("EMPTY_TEST");
    assertThat(smells)
        .filteredOn(s -> "EMPTY_TEST".equals(s.id()))
        .first()
        .satisfies(s -> assertThat(s.severity()).isEqualTo(SmellSeverity.HIGH));
  }

  @Test
  void detect_assertionRouletteFlaggedWhenMostHaveNoMessage(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/test/java"));
    Files.writeString(
        src.resolve("FooTest.java"),
        "import org.junit.jupiter.api.Test;\n"
            + "public class FooTest {\n"
            + "  @Test\n"
            + "  void manyChecks() {\n"
            + "    assertEquals(1, 1);\n"
            + "    assertEquals(2, 2);\n"
            + "    assertEquals(3, 3);\n"
            + "    assertEquals(4, 4);\n"
            + "  }\n"
            + "}");
    assertThat(detector.detect(tmp)).extracting(Smell::id).contains("ASSERTION_ROULETTE");
  }

  @Test
  void detect_assertionRouletteSilentWhenMostHaveMessages(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/test/java"));
    Files.writeString(
        src.resolve("FooTest.java"),
        "import org.junit.jupiter.api.Test;\n"
            + "public class FooTest {\n"
            + "  @Test\n"
            + "  void manyChecks() {\n"
            + "    assertEquals(1, 1, \"a\");\n"
            + "    assertEquals(2, 2, \"b\");\n"
            + "    assertEquals(3, 3, \"c\");\n"
            + "    assertEquals(4, 4, \"d\");\n"
            + "  }\n"
            + "}");
    assertThat(detector.detect(tmp)).extracting(Smell::id).doesNotContain("ASSERTION_ROULETTE");
  }

  @Test
  void detect_sleepyTestFlagged(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/test/java"));
    Files.writeString(
        src.resolve("FooTest.java"),
        "import org.junit.jupiter.api.Test;\n"
            + "public class FooTest {\n"
            + "  @Test\n"
            + "  void slow() throws Exception {\n"
            + "    Thread.sleep(100);\n"
            + "    assertEquals(1, 1);\n"
            + "  }\n"
            + "}");
    assertThat(detector.detect(tmp)).extracting(Smell::id).contains("SLEEPY_TEST");
  }

  @Test
  void detect_redundantPrintFlagged(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/test/java"));
    Files.writeString(
        src.resolve("FooTest.java"),
        "import org.junit.jupiter.api.Test;\n"
            + "public class FooTest {\n"
            + "  @Test\n"
            + "  void noisy() {\n"
            + "    System.out.println(\"hi\");\n"
            + "    assertEquals(1, 1);\n"
            + "  }\n"
            + "}");
    assertThat(detector.detect(tmp)).extracting(Smell::id).contains("REDUNDANT_PRINT");
  }

  @Test
  void detect_longTestFlagged(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/test/java"));
    StringBuilder code =
        new StringBuilder("import org.junit.jupiter.api.Test;\npublic class FooTest {\n")
            .append("  @Test\n")
            .append("  void lengthy() {\n");
    for (int i = 0; i < 60; i++) {
      code.append("    int x").append(i).append(" = ").append(i).append(";\n");
    }
    code.append("    assertEquals(1, 1);\n  }\n}\n");
    Files.writeString(src.resolve("FooTest.java"), code.toString());
    assertThat(detector.detect(tmp)).extracting(Smell::id).contains("LONG_TEST");
  }

  @Test
  void detect_skipsNonTestFiles(@TempDir Path tmp) throws Exception {
    // Production code with @Test is rare but possible — only files matching the test heuristic
    // are scanned. A file in src/main/java with a body identical to a test should be ignored.
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Production.java"),
        "import org.junit.jupiter.api.Test;\n"
            + "public class Production { @Test void weird() {} }");
    assertThat(detector.detect(tmp)).isEmpty();
  }

  @Test
  void detect_skipsNonTestMethodsInTestFiles(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/test/java"));
    Files.writeString(
        src.resolve("HelperTest.java"),
        "public class HelperTest {\n"
            + "  // No @Test annotation — must not produce smells.\n"
            + "  void helper() {}\n"
            + "}");
    assertThat(detector.detect(tmp)).isEmpty();
  }
}
