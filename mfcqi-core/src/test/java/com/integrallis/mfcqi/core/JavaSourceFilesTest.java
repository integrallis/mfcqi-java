package com.integrallis.mfcqi.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour-parity tests for {@link JavaSourceFiles}. Each test maps to a branch of the original
 * {@code get_python_files} algorithm in {@code mfcqi/core/file_utils.py}.
 */
@Tag("unit")
class JavaSourceFilesTest {

  @Test
  void findAll_returnsJavaFilesSorted(@TempDir Path root) throws Exception {
    Path src = Files.createDirectories(root.resolve("src/main/java/com/example"));
    Path b = Files.writeString(src.resolve("B.java"), "package com.example;");
    Path a = Files.writeString(src.resolve("A.java"), "package com.example;");
    Files.writeString(src.resolve("notes.txt"), "ignored");

    List<Path> files = JavaSourceFiles.findAll(root);
    // Python's `sorted(py_files)` — natural Path order.
    assertThat(files).containsExactly(a, b);
  }

  @Test
  void findAll_excludesGenericBuildAndIdeDirs(@TempDir Path root) throws Exception {
    Path mainSrc = Files.createDirectories(root.resolve("src/main/java"));
    Path keep = Files.writeString(mainSrc.resolve("Keep.java"), "");
    Files.writeString(
        Files.createDirectories(root.resolve("build/generated")).resolve("G.java"), "");
    Files.writeString(Files.createDirectories(root.resolve("dist")).resolve("D.java"), "");
    Files.writeString(
        Files.createDirectories(root.resolve("node_modules/x")).resolve("N.java"), "");

    assertThat(JavaSourceFiles.findAll(root)).containsExactly(keep);
  }

  @Test
  void findAll_excludesJavaToolingDirsNotInPythonSet(@TempDir Path root) throws Exception {
    // These (target, .gradle, .idea, .mvn, .vscode, out, bin) are added to the Python
    // EXCLUDED_DIRS set as the documented Java-specific divergence.
    Path mainSrc = Files.createDirectories(root.resolve("src/main/java"));
    Path keep = Files.writeString(mainSrc.resolve("Keep.java"), "");

    Files.writeString(
        Files.createDirectories(root.resolve("target/generated-sources")).resolve("G.java"), "");
    Files.writeString(Files.createDirectories(root.resolve(".gradle")).resolve("X.java"), "");
    Files.writeString(Files.createDirectories(root.resolve(".idea")).resolve("Y.java"), "");
    Files.writeString(Files.createDirectories(root.resolve("out")).resolve("O.java"), "");
    Files.writeString(Files.createDirectories(root.resolve("bin")).resolve("B.java"), "");

    assertThat(JavaSourceFiles.findAll(root)).containsExactly(keep);
  }

  @Test
  void findAll_excludesAnyDottedPathPart(@TempDir Path root) throws Exception {
    // Port of Python's `part.startswith(".")` rule.
    Path keep =
        Files.writeString(
            Files.createDirectories(root.resolve("src/main/java")).resolve("Keep.java"), "");
    Files.writeString(Files.createDirectories(root.resolve(".secret/lib")).resolve("S.java"), "");

    assertThat(JavaSourceFiles.findAll(root)).containsExactly(keep);
  }

  @Test
  void find_excludeTestsRemovesTestFilesByStemAndParentHeuristic(@TempDir Path root)
      throws Exception {
    // Python heuristic: `"test" in stem.lower() or "tests" in str(parent)`.
    Path src = Files.createDirectories(root.resolve("src/main/java"));
    Path tests = Files.createDirectories(root.resolve("tests"));

    Path keep = Files.writeString(src.resolve("Keep.java"), "");
    Files.writeString(src.resolve("FooTest.java"), ""); // stem contains "test"
    Files.writeString(src.resolve("TestBar.java"), ""); // stem contains "test"
    Files.writeString(tests.resolve("Helper.java"), ""); // parent path contains "tests"

    assertThat(JavaSourceFiles.find(root, true)).containsExactly(keep);
    assertThat(JavaSourceFiles.find(root, false)).hasSize(4);
  }

  @Test
  void find_singleFileBranchReturnsMatchingFileOrEmpty(@TempDir Path root) throws Exception {
    Path java = Files.writeString(root.resolve("Lone.java"), "");
    Path text = Files.writeString(root.resolve("Lone.txt"), "");

    assertThat(JavaSourceFiles.find(java, false)).containsExactly(java);
    assertThat(JavaSourceFiles.find(text, false)).isEmpty();
  }

  @Test
  void find_singleFileBranchHonoursExcludeTests(@TempDir Path root) throws Exception {
    Path testFile = Files.writeString(root.resolve("FooTest.java"), "");
    assertThat(JavaSourceFiles.find(testFile, false)).containsExactly(testFile);
    assertThat(JavaSourceFiles.find(testFile, true)).isEmpty();
  }

  @Test
  void find_singleFileBranchSkipsExcludedPaths(@TempDir Path root) throws Exception {
    Path build = Files.createDirectories(root.resolve("build"));
    Path generated = Files.writeString(build.resolve("Generated.java"), "");
    assertThat(JavaSourceFiles.find(generated, false)).isEmpty();
  }

  @Test
  void find_returnsEmptyForNullOrMissing() {
    assertThat(JavaSourceFiles.find(null, false)).isEmpty();
    assertThat(JavaSourceFiles.find(Path.of("/this/does/not/exist"), false)).isEmpty();
  }

  @Test
  void shouldAnalyzeFile_returnsFalseForExcludedPath(@TempDir Path root) throws Exception {
    Path build = Files.createDirectories(root.resolve("build"));
    Path generated = Files.writeString(build.resolve("Generated.java"), "");
    assertThat(JavaSourceFiles.shouldAnalyzeFile(generated)).isFalse();
  }
}
