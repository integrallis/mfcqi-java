package com.integrallis.mfcqi.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrallis.mfcqi.badge.BadgeStyle;
import com.integrallis.mfcqi.cli.Main;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("unit")
class BadgeCommandTest {

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
  void parseStyle_mapsAllVariants() {
    assertThat(BadgeCommand.parseStyle("flat")).isEqualTo(BadgeStyle.FLAT);
    assertThat(BadgeCommand.parseStyle("flat-square")).isEqualTo(BadgeStyle.FLAT_SQUARE);
    assertThat(BadgeCommand.parseStyle("plastic")).isEqualTo(BadgeStyle.PLASTIC);
    assertThat(BadgeCommand.parseStyle("for-the-badge")).isEqualTo(BadgeStyle.FOR_THE_BADGE);
    assertThat(BadgeCommand.parseStyle("unknown")).isEqualTo(BadgeStyle.FLAT);
  }

  @Test
  void cli_outputsShieldsIoUrlByDefault(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("X.java"), "public class X { public int v() { return 1; } }");
    int code = new CommandLine(new Main()).execute("badge", tmp.toString());
    assertThat(code).isEqualTo(0);
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("img.shields.io/badge/MFCQI-");
  }
}
