package com.integrallis.mfcqi.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrallis.mfcqi.cli.Main;
import com.integrallis.mfcqi.cli.commands.ModelsCommand.ListSubcommand;
import com.integrallis.mfcqi.cli.commands.ModelsCommand.PullSubcommand;
import com.integrallis.mfcqi.cli.commands.ModelsCommand.RecommendSubcommand;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

@Tag("unit")
class ModelsCommandTest {

  private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void redirect() {
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restore() {
    System.setOut(originalOut);
    System.setErr(originalErr);
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

  @Test
  void streamPullStatus_printsEachUniqueStatus() {
    String body =
        "{\"status\":\"pulling manifest\"}\n"
            + "{\"status\":\"downloading\",\"completed\":1024,\"total\":1024}\n"
            + "{\"status\":\"downloading\",\"completed\":2048,\"total\":2048}\n"
            + "{\"status\":\"verifying sha256 digest\"}\n"
            + "{\"status\":\"success\"}\n";
    int code =
        PullSubcommand.streamPullStatus(
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    assertThat(code).isZero();
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out)
        .contains("pulling manifest")
        .contains("downloading")
        .contains("verifying sha256 digest")
        .contains("success");
  }

  @Test
  void streamPullStatus_returnsTwoOnError() {
    String body = "{\"error\":\"manifest not found\"}\n";
    int code =
        PullSubcommand.streamPullStatus(
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    assertThat(code).isEqualTo(2);
    assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("manifest not found");
  }

  @Test
  void buildRecommendations_findsCodeGeneralAndHighEndModels() {
    List<String> models =
        Arrays.asList("codellama:7b", "llama3.1:8b", "mixtral:8x22b", "qwen2.5:32b");
    List<String> recs = RecommendSubcommand.buildRecommendations(models);
    assertThat(recs).anyMatch(r -> r.contains("PRIMARY") && r.contains("codellama"));
    assertThat(recs).anyMatch(r -> r.contains("ALTERNATIVE") && r.contains("llama3"));
    assertThat(recs).anyMatch(r -> r.contains("HIGH-END") && r.contains("mixtral"));
  }

  @Test
  void buildRecommendations_emptyForUnrecognizedModels() {
    assertThat(RecommendSubcommand.buildRecommendations(Arrays.asList("custom-model:1b")))
        .isEmpty();
  }

  @Test
  void printDownloadRecommendations_listsThreeChoices() {
    RecommendSubcommand.printDownloadRecommendations();
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out)
        .contains("codellama:7b")
        .contains("llama3.1:8b")
        .contains("qwen2.5-coder:7b")
        .contains("ollama pull codellama:7b");
  }

  @Test
  void cli_recommendWithoutInstalledModelsShowsDownloadGuidance() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/tags",
        exchange -> {
          byte[] body = "{\"models\":[]}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    try {
      String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
      int code =
          new CommandLine(new Main()).execute("models", "recommend", "--ollama-endpoint", endpoint);
      assertThat(code).isZero();
      assertThat(stdout.toString(StandardCharsets.UTF_8))
          .contains("Recommended Downloads")
          .contains("codellama:7b");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void installedModels_returnsEmptyOnConnectionFailure() {
    // Bogus endpoint -> connection refused -> empty list, no exception bubbling.
    assertThat(RecommendSubcommand.installedModels("http://127.0.0.1:1"))
        .isEqualTo(Collections.emptyList());
  }

  @Test
  void modelsWithoutSubcommand_printsUsageHint() {
    int code = new CommandLine(new Main()).execute("models");
    assertThat(code).isZero();
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("Ollama model management").contains("mfcqi models list");
  }

  @Test
  void printModels_returnsTwoOnUnparseableBody() {
    int code = ListSubcommand.printModels("not-json");
    assertThat(code).isEqualTo(2);
    assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("Failed to parse");
  }

  @Test
  void cli_listReturnsTwoOnConnectionFailure() {
    int code =
        new CommandLine(new Main())
            .execute("models", "list", "--ollama-endpoint", "http://127.0.0.1:1");
    assertThat(code).isEqualTo(2);
    assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("Failed to reach Ollama");
  }

  @Test
  void cli_listReturnsTwoOnNon2xxStatus() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/tags",
        exchange -> {
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
        });
    server.start();
    try {
      String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
      int code =
          new CommandLine(new Main()).execute("models", "list", "--ollama-endpoint", endpoint);
      assertThat(code).isEqualTo(2);
      assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("HTTP 500");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void cli_pullReturnsTwoOnConnectionFailure() {
    int code =
        new CommandLine(new Main())
            .execute("models", "pull", "codellama:7b", "--ollama-endpoint", "http://127.0.0.1:1");
    assertThat(code).isEqualTo(2);
    assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("Failed to reach Ollama");
  }

  @Test
  void cli_pullReturnsTwoOnNon2xxStatus() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/pull",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    server.start();
    try {
      String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
      int code =
          new CommandLine(new Main())
              .execute("models", "pull", "codellama:7b", "--ollama-endpoint", endpoint);
      assertThat(code).isEqualTo(2);
      assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("HTTP 404");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void cli_pullStreamsStatusAgainstStubServer() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/pull",
        exchange -> {
          byte[] body =
              ("{\"status\":\"pulling manifest\"}\n"
                      + "{\"status\":\"downloading\",\"completed\":1048576,\"total\":2097152}\n"
                      + "{\"status\":\"success\"}\n")
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
          new CommandLine(new Main())
              .execute("models", "pull", "codellama:7b", "--ollama-endpoint", endpoint);
      assertThat(code).isZero();
      assertThat(stdout.toString(StandardCharsets.UTF_8))
          .contains("pulling manifest")
          .contains("success");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void streamPullStatus_printsProgressForRepeatedStatus() {
    String body =
        "{\"status\":\"downloading\",\"completed\":1048576,\"total\":2097152}\n"
            + "{\"status\":\"downloading\",\"completed\":2097152,\"total\":2097152}\n";
    int code =
        PullSubcommand.streamPullStatus(
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    assertThat(code).isZero();
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("downloading").contains("MB").contains("%");
  }

  @Test
  void cli_recommendListsInstalledModels() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/tags",
        exchange -> {
          byte[] body =
              ("{\"models\":[{\"name\":\"codellama:7b\",\"size\":1048576}]}")
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
          new CommandLine(new Main()).execute("models", "recommend", "--ollama-endpoint", endpoint);
      assertThat(code).isZero();
      assertThat(stdout.toString(StandardCharsets.UTF_8))
          .contains("Your Available Models")
          .contains("PRIMARY");
    } finally {
      server.stop(0);
    }
  }
}
