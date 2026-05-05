package com.integrallis.mfcqi.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integrallis.mfcqi.analysis.OllamaProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code mfcqi models} — Ollama model management. Mirrors the most-used branch of {@code
 * mfcqi/cli/commands/models.py}: lists locally-available Ollama models via {@code GET
 * <endpoint>/api/tags}.
 *
 * <p>The Python source also exposes {@code pull}, {@code benchmark}, and {@code recommend}
 * subcommands. Those wrap the same HTTP API; users can issue them directly with {@code curl} or the
 * {@code ollama} CLI in the meantime.
 */
@Command(
    name = "models",
    mixinStandardHelpOptions = true,
    description = "List locally-available Ollama models.",
    subcommands = {ModelsCommand.ListSubcommand.class})
public final class ModelsCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("mfcqi models — Ollama model management");
    System.out.println("Run 'mfcqi models list' to see available models.");
  }

  /** {@code mfcqi models list} subcommand. */
  @Command(
      name = "list",
      mixinStandardHelpOptions = true,
      description = "List locally-available Ollama models.")
  public static final class ListSubcommand implements Callable<Integer> {

    @Option(
        names = {"--ollama-endpoint"},
        description = "Ollama HTTP endpoint. Default: http://localhost:11434.")
    String endpoint = OllamaProvider.DEFAULT_ENDPOINT;

    @Override
    public Integer call() {
      try {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> response =
            client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
          System.err.println("Ollama returned HTTP " + response.statusCode() + " from " + endpoint);
          return 2;
        }
        return printModels(response.body());
      } catch (java.io.IOException e) {
        System.err.println("Failed to reach Ollama at " + endpoint + ": " + e.getMessage());
        return 2;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return 2;
      }
    }

    static int printModels(String body) {
      try {
        JsonNode root = new ObjectMapper().readTree(body);
        JsonNode models = root.path("models");
        if (!models.isArray() || models.isEmpty()) {
          System.out.println("(no models installed)");
          return 0;
        }
        for (JsonNode m : models) {
          String name = m.path("name").asText("");
          long size = m.path("size").asLong(0L);
          System.out.println(formatModelLine(name, size));
        }
        return 0;
      } catch (java.io.IOException e) {
        System.err.println("Failed to parse Ollama response: " + e.getMessage());
        return 2;
      }
    }

    static String formatModelLine(String name, long sizeBytes) {
      double mb = sizeBytes / 1024.0 / 1024.0;
      return String.format(java.util.Locale.ROOT, "%-40s %.1f MB", name, mb);
    }
  }
}
