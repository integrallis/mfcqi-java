package com.integrallis.mfcqi.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal {@code .env} file loader. Reads {@code KEY=VALUE} entries from a file (typically the
 * project root) and exposes them as a {@link Map}. Used by {@link
 * AnalysisConfig#fromEnvironmentAndDotenv(java.nio.file.Path)} so LLM integration tests can pick up
 * API keys from a developer-local {@code .env} without exporting them in the shell.
 *
 * <p>Parsing rules — kept simple to avoid a third-party dotenv dependency:
 *
 * <ul>
 *   <li>Lines beginning with {@code #} (after trimming) are comments, ignored.
 *   <li>Empty / whitespace-only lines are ignored.
 *   <li>Each entry is {@code KEY=VALUE}. The first {@code =} is the separator; subsequent {@code =}
 *       characters belong to the value.
 *   <li>Surrounding double or single quotes on the value are stripped.
 *   <li>An optional leading {@code export } prefix on the key is stripped (matches Bash {@code set
 *       -a} idioms).
 *   <li>Multi-line values, escape sequences, and variable substitution are NOT supported.
 * </ul>
 *
 * <p>Files containing API keys must NEVER be committed; {@code .env} and {@code *.env} are in the
 * project's {@code .gitignore}.
 */
public final class DotEnv {

  private DotEnv() {}

  /** Parse a {@code .env} file. Returns an empty map if the file does not exist. */
  public static Map<String, String> load(Path file) {
    if (file == null || !Files.isRegularFile(file)) {
      return Collections.emptyMap();
    }
    try {
      return parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    } catch (IOException e) {
      return Collections.emptyMap();
    }
  }

  /** Walk up from {@code start} looking for a {@code .env} file. */
  public static Optional<Path> findUp(Path start) {
    Path current = start == null ? Path.of(".").toAbsolutePath() : start.toAbsolutePath();
    while (current != null) {
      Path candidate = current.resolve(".env");
      if (Files.isRegularFile(candidate)) {
        return Optional.of(candidate);
      }
      current = current.getParent();
    }
    return Optional.empty();
  }

  /** Parse the textual contents of a {@code .env} file. Public for unit testing. */
  public static Map<String, String> parse(String content) {
    Map<String, String> out = new LinkedHashMap<>();
    if (content == null || content.isEmpty()) {
      return out;
    }
    for (String rawLine : content.split("\\r?\\n", -1)) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      if (line.startsWith("export ")) {
        line = line.substring("export ".length()).trim();
      }
      int eq = line.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      String key = line.substring(0, eq).trim();
      String value = line.substring(eq + 1).trim();
      value = unquote(value);
      if (!key.isEmpty()) {
        out.put(key, value);
      }
    }
    return out;
  }

  private static String unquote(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }
}
