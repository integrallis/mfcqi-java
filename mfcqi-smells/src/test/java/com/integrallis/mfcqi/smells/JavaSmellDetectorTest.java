package com.integrallis.mfcqi.smells;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class JavaSmellDetectorTest {

  private final JavaSmellDetector detector = new JavaSmellDetector();

  @Test
  void name_isJavaSmells() {
    assertThat(detector.name()).isEqualTo("java-smells");
  }

  @Test
  void detect_cleanCodebaseHasNoSmells(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Tiny.java"), "public class Tiny { public int v() { return 1; } }");
    assertThat(detector.detect(tmp)).isEmpty();
  }

  @Test
  void detect_flagsLongMethod(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    StringBuilder body = new StringBuilder("public class Big {\n  public void run() {\n");
    for (int i = 0; i < 50; i++) {
      body.append("    int x").append(i).append(" = ").append(i).append(";\n");
    }
    body.append("  }\n}\n");
    Files.writeString(src.resolve("Big.java"), body.toString());

    List<Smell> smells = detector.detect(tmp);
    assertThat(smells).extracting(Smell::id).contains("LONG_METHOD");
  }

  @Test
  void detect_flagsLongParameterList(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Wide.java"),
        "public class Wide { public void f(int a, int b, int c, int d, int e, int f, int g) {} }");
    assertThat(detector.detect(tmp)).extracting(Smell::id).contains("LONG_PARAMETER_LIST");
  }

  @Test
  void detect_flagsGodClass(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    StringBuilder code = new StringBuilder("public class God {\n");
    for (int i = 0; i < 25; i++) {
      code.append("  public void m").append(i).append("() {}\n");
    }
    code.append("}\n");
    Files.writeString(src.resolve("God.java"), code.toString());

    assertThat(detector.detect(tmp)).extracting(Smell::id).contains("GOD_CLASS");
  }

  @Test
  void detect_flagsDeepInheritanceWithinFile(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Tower.java"),
        "public class Tower {\n"
            + "  public static class L0 {}\n"
            + "  public static class L1 extends L0 {}\n"
            + "  public static class L2 extends L1 {}\n"
            + "  public static class L3 extends L2 {}\n"
            + "  public static class L4 extends L3 {}\n"
            + "}");
    assertThat(detector.detect(tmp)).extracting(Smell::id).contains("DEEP_INHERITANCE");
  }

  @Test
  void detect_flagsHighCoupling(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    StringBuilder code = new StringBuilder("public class Hub {\n");
    for (int i = 0; i < 12; i++) {
      code.append("  public Type").append(i).append(" field").append(i).append(";\n");
    }
    code.append("}\n");
    Files.writeString(src.resolve("Hub.java"), code.toString());

    assertThat(detector.detect(tmp)).extracting(Smell::id).contains("HIGH_COUPLING");
  }

  @Test
  void detect_emitsToolFieldOnEverySmell(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("X.java"),
        "public class X { public void f(int a, int b, int c, int d, int e, int f, int g) {} }");
    assertThat(detector.detect(tmp)).allMatch(s -> "java-smells".equals(s.tool()));
  }
}
