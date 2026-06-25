package com.integrallis.mfcqi.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrallis.mfcqi.cli.Main;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

@Tag("unit")
class ConfigCommandTest {

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
  void cli_printsConfigurationFields() {
    int code = new CommandLine(new Main()).execute("config");
    assertThat(code).isZero();
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out)
        .contains("model:")
        .contains("temperature:")
        .contains("max_tokens:")
        .contains("timeout_seconds:")
        .contains("anthropic_api_key:")
        .contains("openai_api_key:")
        .contains("Supported models:");
  }

  @Test
  void cli_apiKeyLinesShowSetOrUnsetMarker() {
    int code = new CommandLine(new Main()).execute("config");
    assertThat(code).isZero();
    String out = stdout.toString(StandardCharsets.UTF_8);
    // Deterministic regardless of whether the host has API keys exported.
    assertThat(out).containsPattern("anthropic_api_key:\\s+<(set|unset)>");
    assertThat(out).containsPattern("openai_api_key:\\s+<(set|unset)>");
  }

  @Test
  void cli_listsAtLeastOneSupportedModel() {
    int code = new CommandLine(new Main()).execute("config");
    assertThat(code).isZero();
    assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("claude");
  }
}
