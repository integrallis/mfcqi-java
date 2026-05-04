package com.integrallis.mfcqi.secrets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans a codebase for hard-coded secrets. Java port of the {@code detect-secrets} algorithm:
 *
 * <ul>
 *   <li>A regex catalog of well-known secret patterns (AWS access keys, GitHub tokens, JWTs,
 *       Slack/Stripe keys, Google API keys, RSA/SSH private-key headers, generic high-entropy
 *       Base64 / hex strings).
 *   <li>A Shannon-entropy filter that flags suspicious string literals: Base64 strings with entropy
 *       ≥ 4.5 and length ≥ 20, hex strings with entropy ≥ 3.0 and length ≥ 32.
 * </ul>
 *
 * <p>Mirrors detect-secrets' detector model: each plugin emits {@link SecretFinding} records.
 * Findings are deduplicated per (file, line, hashedValue) triple to avoid double-counting when
 * multiple detectors hit the same string.
 */
public final class SecretScanner {

  /** SonarSource Base64 entropy default. */
  public static final double DEFAULT_BASE64_ENTROPY = 4.5;

  /** detect-secrets default for hex strings. */
  public static final double DEFAULT_HEX_ENTROPY = 3.0;

  private static final Pattern BASE64_TOKEN = Pattern.compile("[A-Za-z0-9+/=]{20,}");
  private static final Pattern HEX_TOKEN = Pattern.compile("\\b[A-Fa-f0-9]{32,}\\b");

  private final List<NamedPattern> patterns;
  private final double base64EntropyThreshold;
  private final double hexEntropyThreshold;

  public SecretScanner() {
    this(defaultPatterns(), DEFAULT_BASE64_ENTROPY, DEFAULT_HEX_ENTROPY);
  }

  public SecretScanner(
      List<NamedPattern> patterns, double base64EntropyThreshold, double hexEntropyThreshold) {
    this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
    this.base64EntropyThreshold = base64EntropyThreshold;
    this.hexEntropyThreshold = hexEntropyThreshold;
  }

  /** Scan a single file's contents and return all findings. */
  public List<SecretFinding> scanFile(Path file) {
    String content;
    try {
      content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    } catch (IOException e) {
      // Match detect-secrets behaviour: silently skip unreadable files.
      return Collections.emptyList();
    }
    List<SecretFinding> findings = new ArrayList<>();
    String[] lines = content.split("\\r?\\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      int lineNumber = i + 1;
      // Catalog patterns:
      for (NamedPattern p : patterns) {
        Matcher m = p.pattern.matcher(line);
        while (m.find()) {
          findings.add(new SecretFinding(file, lineNumber, p.name, hashSha1(m.group())));
        }
      }
      // Entropy filters: Base64-style and hex-style high-entropy strings.
      Matcher b = BASE64_TOKEN.matcher(line);
      while (b.find()) {
        String token = b.group();
        if (shannonEntropy(token, BASE64_ALPHABET) >= base64EntropyThreshold) {
          findings.add(
              new SecretFinding(file, lineNumber, "Base64HighEntropyString", hashSha1(token)));
        }
      }
      Matcher h = HEX_TOKEN.matcher(line);
      while (h.find()) {
        String token = h.group();
        if (shannonEntropy(token, HEX_ALPHABET) >= hexEntropyThreshold) {
          findings.add(
              new SecretFinding(file, lineNumber, "HexHighEntropyString", hashSha1(token)));
        }
      }
    }
    return dedupe(findings);
  }

  /**
   * Walk {@code root} for text files and aggregate findings. Mirrors detect-secrets' {@code
   * scan_directory}.
   */
  public List<SecretFinding> scanDirectory(Path root) {
    if (root == null || !Files.isDirectory(root)) {
      return Collections.emptyList();
    }
    List<SecretFinding> all = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(root)) {
      walk.filter(Files::isRegularFile)
          .filter(SecretScanner::isLikelyTextFile)
          .forEach(p -> all.addAll(scanFile(p)));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to walk " + root, e);
    }
    return all;
  }

  private static List<SecretFinding> dedupe(List<SecretFinding> findings) {
    java.util.LinkedHashMap<String, SecretFinding> seen = new java.util.LinkedHashMap<>();
    for (SecretFinding f : findings) {
      String key = f.file().toString() + ":" + f.lineNumber() + ":" + f.hashedValue();
      seen.putIfAbsent(key, f);
    }
    return new ArrayList<>(seen.values());
  }

  private static boolean isLikelyTextFile(Path p) {
    String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
    int dot = name.lastIndexOf('.');
    if (dot < 0) {
      return false;
    }
    String ext = name.substring(dot + 1);
    return TEXT_EXTENSIONS.contains(ext);
  }

  private static final java.util.Set<String> TEXT_EXTENSIONS;

  static {
    java.util.Set<String> exts = new java.util.HashSet<>();
    Collections.addAll(
        exts,
        "java",
        "kt",
        "kts",
        "groovy",
        "scala",
        "py",
        "js",
        "ts",
        "tsx",
        "jsx",
        "rb",
        "go",
        "rs",
        "c",
        "cc",
        "cpp",
        "h",
        "hpp",
        "cs",
        "php",
        "swift",
        "yaml",
        "yml",
        "json",
        "xml",
        "properties",
        "toml",
        "ini",
        "conf",
        "env",
        "sh",
        "bash",
        "ps1",
        "dockerfile",
        "tf",
        "tfvars",
        "md",
        "txt",
        "html");
    TEXT_EXTENSIONS = Collections.unmodifiableSet(exts);
  }

  /** Default detect-secrets-equivalent regex catalog. */
  public static List<NamedPattern> defaultPatterns() {
    return Arrays.asList(
        // AWS Access Key ID — 20 char fixed alpha-numeric prefix
        NamedPattern.of(
            "AWSKeyDetector",
            "(?<![A-Z0-9])(AKIA|ASIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASCA)[A-Z0-9]{16}(?![A-Z0-9])"),
        // GitHub fine-grained / classic token
        NamedPattern.of(
            "GitHubTokenDetector",
            "ghp_[A-Za-z0-9]{36,}|gho_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{82,}"),
        // Slack token
        NamedPattern.of("SlackDetector", "xox[abprs]-[A-Za-z0-9-]{10,}"),
        // Stripe live secret key
        NamedPattern.of("StripeDetector", "sk_live_[A-Za-z0-9]{24,}"),
        // Google API key
        NamedPattern.of("CloudantDetector", "AIza[0-9A-Za-z\\-_]{35}"),
        // JWT token
        NamedPattern.of(
            "JwtTokenDetector",
            "eyJ[A-Za-z0-9_=-]{8,}\\.eyJ[A-Za-z0-9_=-]{8,}\\.[A-Za-z0-9_.+/=-]+"),
        // Private key headers
        NamedPattern.of(
            "PrivateKeyDetector",
            "-----BEGIN (RSA|DSA|EC|OPENSSH|PGP|ENCRYPTED) PRIVATE KEY( BLOCK)?-----"),
        // Basic auth in URL
        NamedPattern.of("BasicAuthDetector", "://[^/\\s:@]+:[^/\\s:@]+@"),
        // Generic high-confidence "key/secret/token =" assignments with quoted value.
        NamedPattern.of(
            "KeywordDetector",
            "(?i)(api[_-]?key|secret|token|password|passwd|pwd|client[_-]?secret)\\s*[:=]\\s*[\"']([A-Za-z0-9!@#$%^&*()_+/=\\-]{8,})[\"']"));
  }

  /** Shannon entropy of {@code s} relative to the symbol alphabet {@code charset}. */
  static double shannonEntropy(String s, String charset) {
    if (s == null || s.isEmpty()) {
      return 0.0;
    }
    double entropy = 0.0;
    for (int i = 0; i < charset.length(); i++) {
      char c = charset.charAt(i);
      double freq = 0.0;
      for (int j = 0; j < s.length(); j++) {
        if (s.charAt(j) == c) {
          freq++;
        }
      }
      if (freq > 0.0) {
        double p = freq / s.length();
        entropy -= p * (Math.log(p) / Math.log(2.0));
      }
    }
    return entropy;
  }

  private static final String BASE64_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
  private static final String HEX_ALPHABET = "0123456789abcdefABCDEF";

  static String hashSha1(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format("%02x", b & 0xff));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 unavailable", e);
    }
  }

  /** A named regex detector — equivalent to a detect-secrets plugin. */
  public static final class NamedPattern {
    public final String name;
    public final Pattern pattern;

    private NamedPattern(String name, Pattern pattern) {
      this.name = name;
      this.pattern = pattern;
    }

    public static NamedPattern of(String name, String regex) {
      return new NamedPattern(name, Pattern.compile(regex));
    }
  }
}
