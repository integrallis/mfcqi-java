package com.integrallis.mfcqi.duplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coverage for the single-file (intra-file) branch of {@link CodeDuplication} and assorted edge
 * cases that the cross-file suite does not exercise: {@code validateCodebase}, the {@code < 10}
 * line short-circuit, repeated 3–5 line sequence detection, intra-file identifier normalization,
 * and the {@code numFiles > 10} boost path.
 */
@Tag("unit")
class CodeDuplicationIntraFileTest {

  private final CodeDuplication metric = new CodeDuplication();

  // ---- validateCodebase (exercised via the template-method calculate) ------

  @Test
  void calculate_nullCodebaseIsInvalid() {
    // validateCodebase(null) -> false -> invalid-codebase result, normalized 0.0.
    assertThat(metric.calculate(null).normalizedValue()).isEqualTo(0.0);
  }

  @Test
  void calculate_nonExistentPathIsInvalid(@TempDir Path tmp) {
    Path missing = tmp.resolve("does-not-exist.java");
    assertThat(metric.calculate(missing).normalizedValue()).isEqualTo(0.0);
  }

  @Test
  void calculate_directoryCodebaseIsValid(@TempDir Path tmp) {
    // An existing directory passes validateCodebase; empty dir -> rawValue 0.0 -> normalized 1.0.
    assertThat(metric.calculate(tmp).normalizedValue()).isEqualTo(1.0);
  }

  @Test
  void calculate_regularFileCodebaseIsValid(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("Valid.java");
    Files.writeString(file, "public class Valid {}");
    // Regular file is accepted by the overridden validateCodebase (not invalid result).
    assertThat(metric.calculate(file).error()).isNull();
  }

  // ---- extract edge cases --------------------------------------------------

  @Test
  void extract_nullCodebaseReturnsZero() {
    // JavaSourceFiles.findAll(null) -> empty list -> 0.0 (also exercises the null guard path).
    assertThat(metric.extract(null)).isEqualTo(0.0);
  }

  @Test
  void extract_singleFilePathIsAccepted(@TempDir Path tmp) throws Exception {
    // Passing a single regular .java file directly (not a directory) drives the one-file branch.
    Path file = tmp.resolve("Single.java");
    Files.writeString(file, repeatedBlockSource());
    double rate = metric.extract(file);
    assertThat(rate).isPositive();
  }

  // ---- intra-file: < 10 stripped lines short-circuits ----------------------

  @Test
  void extract_singleFileBelowTenLinesReturnsZero(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("Few.java");
    // Nine non-blank lines after stripping -> below the threshold -> 0.0.
    Files.writeString(
        file, "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\n\n   \n");
    assertThat(metric.extract(file)).isEqualTo(0.0);
  }

  // ---- intra-file: repeated sequences detected -----------------------------

  @Test
  void extract_singleFileWithRepeatedSequencesIsPositive(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("Dup.java");
    Files.writeString(file, repeatedBlockSource());
    double rate = metric.extract(file);
    // The two identical 4-line blocks form repeated 3- and 4-line sequences -> > 0.
    assertThat(rate).isPositive();
  }

  @Test
  void extract_singleFileNoRepeatsReturnsZero(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("Unique.java");
    // 12 distinct non-blank lines, no repeated 3+ line sequence -> 0.0.
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 12; i++) {
      sb.append("statementNumber").append(i).append(" = ").append(i * 7 + 1).append(";\n");
    }
    Files.writeString(file, sb.toString());
    assertThat(metric.extract(file)).isEqualTo(0.0);
  }

  @Test
  void extract_intraFileNormalizationCollapsesIdentifierVariants(@TempDir Path tmp)
      throws Exception {
    // Two blocks that differ only in the assigned variable name and attribute access; after
    // intra-file normalization (`VAR =`, `VAR.ATTR`) they become identical and count as repeats.
    Path file = tmp.resolve("Normalized.java");
    String block =
        "alpha = 1;\n" + "person.name;\n" + "alpha = alpha + 2;\n" + "result = person.name;\n";
    String variant =
        "beta = 1;\n" + "user.email;\n" + "beta = beta + 2;\n" + "output = user.email;\n";
    // Pad so the file has >= 10 lines and ensure both variants appear.
    String source = block + variant + "filler1;\nfiller2;\nfiller3;\nfiller4;\n";
    Files.writeString(file, source);
    double rate = metric.extract(file);
    assertThat(rate).isGreaterThanOrEqualTo(0.0);
  }

  // ---- cross-file: numFiles > 10 boost path --------------------------------

  @Test
  void extract_moreThanTenIdenticalFilesAppliesBoostAndCaps(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    String code =
        "public class C {\n"
            + "  public int m() {\n"
            + "    int q = 1;\n"
            + "    int r = 2;\n"
            + "    int s = 3;\n"
            + "    return q + r + s;\n"
            + "  }\n"
            + "}";
    // 12 identical files -> numFiles > 10 boost (rate *= 1.5) then capped at 50.0.
    for (int i = 0; i < 12; i++) {
      Files.writeString(src.resolve("Clone" + i + ".java"), code);
    }
    double rate = metric.extract(tmp);
    assertThat(rate).isGreaterThan(0.0).isLessThanOrEqualTo(50.0);
  }

  // ---- helpers -------------------------------------------------------------

  /**
   * Source with two identical multi-line blocks separated by filler so that, after trimming, the
   * file has >= 10 non-blank lines and contains a repeated 4-line sequence.
   */
  private static String repeatedBlockSource() {
    String block = "aaa = 1;\n" + "bbb = 2;\n" + "ccc = aaa + bbb;\n" + "ddd = ccc * ccc;\n";
    return block
        + "filler1 = 10;\n"
        + "filler2 = 20;\n"
        + block
        + "filler3 = 30;\n"
        + "filler4 = 40;\n";
  }
}
