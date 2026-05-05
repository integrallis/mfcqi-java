package com.integrallis.mfcqi.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integrallis.mfcqi.analysis.OllamaProvider;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code mfcqi models} — Ollama model management. Direct port of {@code
 * mfcqi/cli/commands/models.py}.
 *
 * <p>Implemented subcommands:
 *
 * <ul>
 *   <li>{@code list} — {@code GET /api/tags}, prints names + sizes
 *   <li>{@code pull} — streamed {@code POST /api/pull}, prints status updates as the download
 *       progresses
 *   <li>{@code recommend} — pattern-matches installed models (codellama / code, llama3 / mistral,
 *       mixtral / 20b) and labels them PRIMARY / ALTERNATIVE / HIGH-END; falls back to a hard-coded
 *       download list (verbatim from the Python source) when none are installed
 * </ul>
 *
 * <p>The Python {@code benchmark} subcommand is intentionally skipped — its Python implementation
 * runs cosmetic animations and prints hard-coded star ratings keyed off model name substrings, so
 * porting it would not add useful behaviour.
 */
@Command(
    name = "models",
    mixinStandardHelpOptions = true,
    description = "Ollama model management.",
    subcommands = {
      ModelsCommand.ListSubcommand.class,
      ModelsCommand.PullSubcommand.class,
      ModelsCommand.RecommendSubcommand.class
    })
public final class ModelsCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("mfcqi models — Ollama model management");
    System.out.println(
        "Run 'mfcqi models list', 'mfcqi models pull <name>', or 'mfcqi models recommend'.");
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
      return String.format(Locale.ROOT, "%-40s %.1f MB", name, mb);
    }
  }

  /** {@code mfcqi models pull <NAME>} subcommand — real Ollama {@code /api/pull} stream. */
  @Command(
      name = "pull",
      mixinStandardHelpOptions = true,
      description = "Download an Ollama model (real /api/pull, not the Python animation).")
  public static final class PullSubcommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Model name (e.g., codellama:7b).")
    String modelName;

    @Option(
        names = {"--ollama-endpoint"},
        description = "Ollama HTTP endpoint. Default: http://localhost:11434.")
    String endpoint = OllamaProvider.DEFAULT_ENDPOINT;

    @Override
    public Integer call() {
      try {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String body = "{\"name\":\"" + escape(modelName) + "\",\"stream\":true}";
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/pull"))
                .timeout(Duration.ofMinutes(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<java.io.InputStream> response =
            client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
          System.err.println("Ollama returned HTTP " + response.statusCode() + " for /api/pull");
          return 2;
        }
        return streamPullStatus(response.body());
      } catch (java.io.IOException e) {
        System.err.println("Failed to reach Ollama at " + endpoint + ": " + e.getMessage());
        return 2;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return 2;
      }
    }

    static int streamPullStatus(java.io.InputStream stream) {
      ObjectMapper mapper = new ObjectMapper();
      String lastStatus = null;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.isEmpty()) {
            continue;
          }
          JsonNode node = mapper.readTree(line);
          String status = node.path("status").asText("");
          if (node.has("error")) {
            System.err.println("Ollama error: " + node.path("error").asText());
            return 2;
          }
          if (!status.equals(lastStatus)) {
            System.out.println(status);
            lastStatus = status;
          } else if (node.has("completed") && node.has("total")) {
            long completed = node.path("completed").asLong();
            long total = node.path("total").asLong();
            if (total > 0) {
              System.out.printf(
                  Locale.ROOT,
                  "  %s — %d / %d MB (%.1f%%)%n",
                  status,
                  completed / (1024 * 1024),
                  total / (1024 * 1024),
                  (100.0 * completed) / total);
            }
          }
        }
      } catch (java.io.IOException e) {
        System.err.println("Failed reading pull stream: " + e.getMessage());
        return 2;
      }
      return 0;
    }

    private static String escape(String s) {
      return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
  }

  /** {@code mfcqi models recommend} subcommand — pattern-match recommendations for the user. */
  @Command(
      name = "recommend",
      mixinStandardHelpOptions = true,
      description = "Recommend Ollama models for MFCQI analysis.")
  public static final class RecommendSubcommand implements Callable<Integer> {

    @Option(
        names = {"--ollama-endpoint"},
        description = "Ollama HTTP endpoint. Default: http://localhost:11434.")
    String endpoint = OllamaProvider.DEFAULT_ENDPOINT;

    @Override
    public Integer call() {
      List<String> installed = installedModels(endpoint);
      System.out.println("MFCQI Model Recommendations");
      System.out.println();
      List<String> recommendations = buildRecommendations(installed);
      if (!recommendations.isEmpty()) {
        System.out.println("Your Available Models:");
        for (String r : recommendations) {
          System.out.println("  " + r);
        }
      } else if (installed.isEmpty()) {
        printDownloadRecommendations();
      } else {
        System.out.println("No specialized models found. Consider: ollama pull codellama:7b");
      }
      return 0;
    }

    static List<String> installedModels(String endpoint) {
      try {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> response =
            client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
          return Collections.emptyList();
        }
        JsonNode root = new ObjectMapper().readTree(response.body());
        JsonNode models = root.path("models");
        List<String> names = new ArrayList<>();
        if (models.isArray()) {
          for (JsonNode m : models) {
            String n = m.path("name").asText("");
            if (!n.isEmpty()) {
              names.add(n);
            }
          }
        }
        return names;
      } catch (java.io.IOException e) {
        return Collections.emptyList();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Collections.emptyList();
      }
    }

    /** Verbatim port of Python's {@code _build_recommendations} pattern matchers. */
    static List<String> buildRecommendations(List<String> models) {
      List<String> out = new ArrayList<>();
      String code = findByPatterns(models, "codellama", "code");
      if (code != null) {
        out.add("PRIMARY:    " + code + " — Optimized for code analysis");
      }
      String general = findByPatterns(models, "llama3", "mistral");
      if (general != null) {
        out.add("ALTERNATIVE: " + general + " — Good general purpose");
      }
      String highEnd = findByPatterns(models, "mixtral", "20b");
      if (highEnd != null) {
        out.add("HIGH-END:   " + highEnd + " — Best quality, slower");
      }
      return out;
    }

    private static String findByPatterns(List<String> models, String... patterns) {
      for (String m : models) {
        String lower = m.toLowerCase(Locale.ROOT);
        for (String p : patterns) {
          if (lower.contains(p)) {
            return m;
          }
        }
      }
      return null;
    }

    /** Verbatim port of Python's {@code _display_download_recommendations} content. */
    static void printDownloadRecommendations() {
      System.out.println("Recommended Downloads:");
      System.out.println();
      System.out.println("  codellama:7b      — Best for code analysis (3.8GB)");
      System.out.println("  llama3.1:8b       — Good general purpose (4.7GB)");
      System.out.println("  qwen2.5-coder:7b  — Alternative code specialist (4.1GB)");
      System.out.println();
      System.out.println("Start with: ollama pull codellama:7b");
    }
  }
}
