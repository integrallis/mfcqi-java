package com.integrallis.mfcqi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

@Tag("unit")
class MainTest {

  private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
  private PrintStream originalOut;

  @BeforeEach
  void redirect() {
    originalOut = System.out;
    System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restore() {
    System.setOut(originalOut);
  }

  @Test
  void noSubcommand_printsHint() {
    int code = new CommandLine(new Main()).execute();
    assertThat(code).isZero();
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("Multi-Factor Code Quality Index");
    assertThat(out).contains("--help");
  }

  @Test
  void versionFlag_printsVersion() {
    int code = new CommandLine(new Main()).execute("--version");
    assertThat(code).isZero();
    assertThat(stdout.toString(StandardCharsets.UTF_8))
        .contains("mfcqi-java " + com.integrallis.mfcqi.core.Version.current());
  }

  @Test
  void helpFlag_listsSubcommands() {
    int code = new CommandLine(new Main()).execute("--help");
    assertThat(code).isZero();
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("analyze");
    assertThat(out).contains("badge");
    assertThat(out).contains("config");
    assertThat(out).contains("models");
    assertThat(out).contains("quality-gate");
  }

  @Test
  void run_directlyPrintsHint() {
    new Main().run();
    assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("Run with --help");
  }
}
