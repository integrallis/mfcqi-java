package com.integrallis.mfcqi.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class DocumentationCoverageTest {

  private final DocumentationCoverage metric = new DocumentationCoverage();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.4);
    assertThat(metric.getName()).isEqualTo("Documentation Coverage");
  }

  @Test
  void normalize_clampsAtZeroAndOne() {
    assertThat(metric.normalize(0.0)).isEqualTo(0.0);
    assertThat(metric.normalize(-5.0)).isEqualTo(0.0);
    assertThat(metric.normalize(100.0)).isEqualTo(1.0);
    assertThat(metric.normalize(150.0)).isEqualTo(1.0);
  }

  @Test
  void normalize_isLinearInBetween() {
    assertThat(metric.normalize(50.0)).isCloseTo(0.5, within(1e-9));
    assertThat(metric.normalize(80.0)).isCloseTo(0.8, within(1e-9));
  }

  @Test
  void extract_emptyCodebaseReturnsZero(@TempDir Path tmp) {
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_fullyDocumentedFileReturnsHundred(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    String code =
        "/** Top-level Javadoc serves as the module docstring. */\n"
            + "public class A {\n"
            + "  /** Constructor. */\n"
            + "  public A() {}\n"
            + "  /** Does the thing. */\n"
            + "  public int doIt() { return 1; }\n"
            + "}";
    Files.writeString(src.resolve("A.java"), code);

    // Documentable: 1 (module) + 1 (public class) + 1 (constructor) + 1 (method) = 4
    // Documented: 4 — coverage = 100%
    assertThat(metric.extract(tmp)).isCloseTo(100.0, within(1e-9));
  }

  @Test
  void extract_undocumentedFileReturnsZero(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    String code =
        "public class A {\n" + "  public A() {}\n" + "  public int doIt() { return 1; }\n" + "}";
    Files.writeString(src.resolve("A.java"), code);
    assertThat(metric.extract(tmp)).isCloseTo(0.0, within(1e-9));
  }

  @Test
  void extract_partiallyDocumentedAggregatesAcrossUnits(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    String code =
        "/** Module-level. */\n"
            + "public class A {\n"
            + "  public A() {}\n"
            + "  /** doIt. */\n"
            + "  public int doIt() { return 1; }\n"
            + "}";
    Files.writeString(src.resolve("A.java"), code);

    // Documentable: 1 (module) + 1 (class) + 1 (constructor) + 1 (method) = 4
    // Documented: module + class (same javadoc serves both) + doIt = 3
    // Coverage = 3/4 = 75%
    assertThat(metric.extract(tmp)).isCloseTo(75.0, within(1e-9));
  }

  @Test
  void extract_excludesNonPublicMembers(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    String code =
        "/** module */\n"
            + "public class A {\n"
            + "  private int hidden() { return 0; }\n"
            + "  int packagePrivate() { return 0; }\n"
            + "  protected int protectedMethod() { return 0; }\n"
            + "}";
    Files.writeString(src.resolve("A.java"), code);

    // Documentable: 1 (module) + 1 (public class) = 2 (private/package/protected excluded)
    // Documented: module + class (same javadoc) = 2 -> 100%
    assertThat(metric.extract(tmp)).isCloseTo(100.0, within(1e-9));
  }

  @Test
  void extract_picksUpPackageInfoModuleDoc(@TempDir Path tmp) throws Exception {
    Path pkg = Files.createDirectories(tmp.resolve("src/main/java/com/example"));
    Files.writeString(
        pkg.resolve("package-info.java"), "/** Package docs. */\npackage com.example;");

    // No types in package-info.java — just the module-level entry. With Javadoc -> 100%.
    assertThat(metric.extract(tmp)).isCloseTo(100.0, within(1e-9));
  }

  @Test
  void extract_averagesAcrossFiles(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Documented.java"),
        "/** docs */ public class Documented { /** ctor */ public Documented() {} }");
    Files.writeString(src.resolve("Bare.java"), "public class Bare { public Bare() {} }");

    // Documented.java: 1 (mod) + 1 (class) + 1 (ctor) = 3 documentable, 3 documented
    // Bare.java:       1 (mod) + 1 (class) + 1 (ctor) = 3 documentable, 0 documented
    // Total: 6 documentable, 3 documented -> 50%
    assertThat(metric.extract(tmp)).isCloseTo(50.0, within(1e-9));
  }
}
