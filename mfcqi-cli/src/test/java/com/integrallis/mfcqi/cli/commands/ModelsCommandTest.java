package com.integrallis.mfcqi.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrallis.mfcqi.cli.Main;
import com.integrallis.mfcqi.cli.commands.ModelsCommand.ListSubcommand;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

@Tag("unit")
class ModelsCommandTest {

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
  void formatModelLine_padsAndShowsMegabytes() {
    String line = ListSubcommand.formatModelLine("llama3:latest", 1024L * 1024L * 256);
    assertThat(line).startsWith("llama3:latest").contains("256.0 MB");
  }

  @Test
  void printModels_listsModelsFromResponse() {
    String body =
        "{\"models\":[{\"name\":\"llama3:latest\",\"size\":1048576},{\"name\":\"qwen:7b\",\"size\":2097152}]}";
    int code = ListSubcommand.printModels(body);
    assertThat(code).isZero();
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("llama3:latest").contains("qwen:7b");
  }

  @Test
  void printModels_handlesEmptyList() {
    int code = ListSubcommand.printModels("{\"models\":[]}");
    assertThat(code).isZero();
    assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("(no models installed)");
  }

  @Test
  void cli_modelsListAgainstStubServerPrintsTags() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/tags",
        exchange -> {
          byte[] body =
              ("{\"models\":[{\"name\":\"llama3:latest\",\"size\":1048576}]}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    try {
      String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
      int code =
          new CommandLine(new Main()).execute("models", "list", "--ollama-endpoint", endpoint);
      assertThat(code).isZero();
      assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("llama3:latest");
    } finally {
      server.stop(0);
    }
  }
}
