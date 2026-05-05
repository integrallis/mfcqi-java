package com.integrallis.mfcqi.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class DotEnvTest {

  @Test
  void parse_basicKeyValue() {
    Map<String, String> env = DotEnv.parse("FOO=bar\nBAZ=qux\n");
    assertThat(env).containsEntry("FOO", "bar").containsEntry("BAZ", "qux");
  }

  @Test
  void parse_skipsCommentsAndBlankLines() {
    String content =
        "# top comment\n" + "\n" + "FOO=bar\n" + "  # indented comment\n" + "BAZ=qux\n";
    Map<String, String> env = DotEnv.parse(content);
    assertThat(env).hasSize(2);
  }

  @Test
  void parse_stripsDoubleAndSingleQuotes() {
    Map<String, String> env = DotEnv.parse("A=\"hello\"\nB='world'\nC=plain");
    assertThat(env)
        .containsEntry("A", "hello")
        .containsEntry("B", "world")
        .containsEntry("C", "plain");
  }

  @Test
  void parse_keepsEqualsInsideValue() {
    // Anthropic / OpenAI keys often contain "=" — must preserve everything after the first "=".
    Map<String, String> env = DotEnv.parse("KEY=base64==signature\n");
    assertThat(env).containsEntry("KEY", "base64==signature");
  }

  @Test
  void parse_stripsExportPrefix() {
    Map<String, String> env = DotEnv.parse("export FOO=bar\n");
    assertThat(env).containsEntry("FOO", "bar");
  }

  @Test
  void parse_ignoresLinesWithoutEquals() {
    Map<String, String> env = DotEnv.parse("not a key value\nFOO=bar\n");
    assertThat(env).containsOnlyKeys("FOO");
  }

  @Test
  void load_returnsEmptyForMissingFile(@TempDir Path tmp) {
    assertThat(DotEnv.load(tmp.resolve("nope.env"))).isEmpty();
  }

  @Test
  void load_readsRealFile(@TempDir Path tmp) throws Exception {
    Path file = Files.writeString(tmp.resolve(".env"), "FOO=bar\nBAZ=qux");
    assertThat(DotEnv.load(file)).containsEntry("FOO", "bar").containsEntry("BAZ", "qux");
  }

  @Test
  void findUp_locatesEnvFileInAncestor(@TempDir Path tmp) throws Exception {
    Path nested = Files.createDirectories(tmp.resolve("a/b/c"));
    Path env = Files.writeString(tmp.resolve(".env"), "FOO=bar");
    assertThat(DotEnv.findUp(nested)).contains(env);
  }

  @Test
  void findUp_returnsEmptyWhenNoEnvFileFound(@TempDir Path tmp) throws Exception {
    Path nested = Files.createDirectories(tmp.resolve("x/y/z"));
    assertThat(DotEnv.findUp(nested)).isEmpty();
  }
}
