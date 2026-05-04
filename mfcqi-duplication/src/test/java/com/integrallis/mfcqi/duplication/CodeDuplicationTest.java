package com.integrallis.mfcqi.duplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class CodeDuplicationTest {

  private final CodeDuplication metric = new CodeDuplication();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.6);
    assertThat(metric.getName()).isEqualTo("Code Duplication");
  }

  @Test
  void normalize_followsPythonPiecewiseCurve() {
    assertThat(metric.normalize(0.0)).isEqualTo(1.0);
    assertThat(metric.normalize(50.0)).isEqualTo(0.0);
    assertThat(metric.normalize(60.0)).isEqualTo(0.0);

    // Spot-check the four buckets (values picked at the upper bound of each).
    assertThat(metric.normalize(5.0)).isCloseTo(0.9, within(1e-9)); // 1.0 - 5/50
    assertThat(metric.normalize(15.0)).isCloseTo(0.5, within(1e-9)); // 0.9 - 10/10*0.4
    assertThat(metric.normalize(30.0)).isCloseTo(0.1, within(1e-9)); // 0.5 - 15/15*0.4
    assertThat(metric.normalize(45.0)).isCloseTo(0.025, within(1e-9)); // 0.1 - 15/20*0.1
  }

  @Test
  void normalize_isLinearWithinBuckets() {
    // 10 falls in [5, 15) -> 0.9 - 5/10*0.4 = 0.7
    assertThat(metric.normalize(10.0)).isCloseTo(0.7, within(1e-9));
    // 22.5 falls in [15, 30) -> 0.5 - 7.5/15*0.4 = 0.3
    assertThat(metric.normalize(22.5)).isCloseTo(0.3, within(1e-9));
  }

  @Test
  void normalizeLine_stripsWhitespaceAndNormalizesIdentifiers() {
    // Python: removes all whitespace, replaces calculate/compute/get/set/find words with FUNC
    // and length/width/height/size/count/num with VAR.
    assertThat(CodeDuplication.normalizeLine("int x = a + b ;")).isEqualTo("intx=a+b;");
    assertThat(CodeDuplication.normalizeLine("calculateThing()")).isEqualTo("FUNC()");
    assertThat(CodeDuplication.normalizeLine("getValue(x)")).isEqualTo("FUNC(x)");
    assertThat(CodeDuplication.normalizeLine("size = 10")).isEqualTo("VAR=10");
  }

  @Test
  void createCodeBlocks_buildsThreeFourFiveLineWindows() {
    java.util.List<String> lines = java.util.Arrays.asList("a", "b", "c", "d", "e");
    java.util.List<String> blocks = CodeDuplication.createCodeBlocks(lines);
    // Windows of 3 (3 starts: 0..2), 4 (2 starts: 0..1), 5 (1 start) = 6 blocks total.
    assertThat(blocks).hasSize(6);
    assertThat(blocks.get(0)).isEqualTo("a\nb\nc");
    assertThat(blocks.get(blocks.size() - 1)).isEqualTo("a\nb\nc\nd\ne");
  }

  @Test
  void extract_emptyCodebaseReturnsZero(@TempDir Path tmp) {
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_singleSmallFileHasZeroIntraFileDuplication(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("Tiny.java"), "public class Tiny { int v() { return 1; } }");
    // < 10 lines after stripping -> 0.0
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_twoIdenticalFilesProducePositiveDuplication(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    String code =
        "public class A {\n"
            + "  public int m1(int a, int b) {\n"
            + "    int total = a + b;\n"
            + "    int squared = total * total;\n"
            + "    int adjusted = squared - 1;\n"
            + "    return adjusted;\n"
            + "  }\n"
            + "}";
    Files.writeString(src.resolve("First.java"), code);
    Files.writeString(src.resolve("Second.java"), code);
    double rate = metric.extract(tmp);
    assertThat(rate).isPositive();
  }

  @Test
  void extract_disjointFilesHaveZeroDuplication(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Alpha.java"),
        "public class Alpha {\n"
            + "  public int alpha() {\n"
            + "    int aaa = 11;\n"
            + "    int bbb = 22;\n"
            + "    int ccc = 33;\n"
            + "    return aaa + bbb + ccc;\n"
            + "  }\n"
            + "}");
    Files.writeString(
        src.resolve("Beta.java"),
        "public class Beta {\n"
            + "  public int beta() {\n"
            + "    String xxx = \"hello\";\n"
            + "    String yyy = \"world\";\n"
            + "    String zzz = xxx + yyy;\n"
            + "    return zzz.length();\n"
            + "  }\n"
            + "}");
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_capsAtFiftyPercent(@TempDir Path tmp) throws Exception {
    // Three identical files — duplication rate should be capped at 50%.
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    String code =
        "public class A {\n"
            + "  public int m() {\n"
            + "    int q = 1;\n"
            + "    int r = 2;\n"
            + "    int s = 3;\n"
            + "    return q + r + s;\n"
            + "  }\n"
            + "}";
    Files.writeString(src.resolve("One.java"), code);
    Files.writeString(src.resolve("Two.java"), code);
    Files.writeString(src.resolve("Three.java"), code);
    assertThat(metric.extract(tmp)).isLessThanOrEqualTo(50.0);
  }

  @Test
  void extract_skipsLineComments(@TempDir Path tmp) throws Exception {
    // Per the Python port: lines beginning with the comment marker are stripped before hashing.
    // Two files with identical bodies — one annotated with line comments — should still be
    // detected as duplicates.
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Plain.java"),
        "public class Plain {\n"
            + "  public int m() {\n"
            + "    int xa = 1;\n"
            + "    int xb = 2;\n"
            + "    int xc = 3;\n"
            + "    return xa + xb + xc;\n"
            + "  }\n"
            + "}");
    Files.writeString(
        src.resolve("Annotated.java"),
        "public class Annotated {\n"
            + "  // helper method below\n"
            + "  public int m() {\n"
            + "    // first\n"
            + "    int xa = 1;\n"
            + "    // second\n"
            + "    int xb = 2;\n"
            + "    int xc = 3;\n"
            + "    return xa + xb + xc;\n"
            + "  }\n"
            + "}");
    assertThat(metric.extract(tmp)).isPositive();
  }
}
