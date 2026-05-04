package com.integrallis.mfcqi.duplication;

import com.integrallis.mfcqi.core.JavaSourceFiles;
import com.integrallis.mfcqi.core.Metric;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Code Duplication metric — deterministic hash-based block detection.
 *
 * <p>Direct port of {@code mfcqi/metrics/duplication.py:CodeDuplication}. The Python implementation
 * has two branches: cross-file detection (≥ 2 files) hashes 3/4/5-line normalized sliding-window
 * blocks and counts blocks appearing in multiple files, while single-file detection looks for
 * repeated 3–5 line sequences within the file. Both branches share a normalization pipeline that
 * strips whitespace, replaces common identifier patterns with placeholders, and skips comment
 * lines.
 *
 * <p>Java equivalence:
 *
 * <ul>
 *   <li>File extension: {@code .py} → {@code .java}
 *   <li>Comment marker for line-skipping: Python {@code #} → Java {@code //}. Lines whose trimmed
 *       prefix begins with {@code //} are skipped, mirroring Python's {@code
 *       stripped.startswith("#")} rule. Block comments {@code /* … *}{@code /} are NOT specially
 *       handled — the Python source likewise does not strip Python multi-line strings, so this
 *       preserves the same level of approximation.
 *   <li>Identifier-normalization regexes are translated verbatim (the patterns {@code
 *       calculate|compute|get|set|find} and {@code length|width|height|size|count|num} are
 *       domain-agnostic and apply equally to Java).
 * </ul>
 *
 * <p>Normalization, weight, and name come verbatim from the Python source.
 */
public final class CodeDuplication extends Metric<Double> {

  private static final Logger LOG = LoggerFactory.getLogger(CodeDuplication.class);

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  // Verbatim from Python: r"\b(calculate|compute|get|set|find)\w*\b" -> FUNC
  private static final Pattern FUNC_NAMES =
      Pattern.compile("\\b(calculate|compute|get|set|find)\\w*\\b");
  // Verbatim from Python: r"\b(length|width|height|size|count|num)\b" -> VAR
  private static final Pattern VAR_NAMES =
      Pattern.compile("\\b(length|width|height|size|count|num)\\b");
  // Verbatim from Python: r"\b[a-zA-Z_][a-zA-Z0-9_]*\.(age|name|email)\b" -> VAR.ATTR
  private static final Pattern INTRA_ATTR =
      Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\.(age|name|email)\\b");
  // Verbatim from Python: r"\b[a-zA-Z_][a-zA-Z0-9_]*\s*=" -> VAR =
  private static final Pattern INTRA_ASSIGN = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\s*=");

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null && (Files.isDirectory(codebase) || Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    List<Path> files = JavaSourceFiles.findAll(codebase);
    if (files.isEmpty()) {
      return 0.0;
    }
    if (files.size() == 1) {
      return checkIntraFileDuplication(files.get(0));
    }
    return simpleDuplicationCheck(files);
  }

  /** Port of {@code _simple_duplication_check}. */
  private double simpleDuplicationCheck(List<Path> files) {
    List<Path> sorted = new ArrayList<>(files);
    Collections.sort(sorted);

    List<FileBlock> fileBlocks = extractFileBlocks(sorted);
    if (fileBlocks.isEmpty()) {
      return 0.0;
    }
    Map<String, Set<Integer>> blockFileMap = buildBlockFileMap(fileBlocks);
    int duplicateBlocks = countDuplicateBlocks(blockFileMap);
    return calculateDuplicationRate(duplicateBlocks, blockFileMap, sorted.size());
  }

  /** Port of {@code _extract_file_blocks}. */
  private List<FileBlock> extractFileBlocks(List<Path> sortedFiles) {
    List<FileBlock> out = new ArrayList<>();
    for (int fileIdx = 0; fileIdx < sortedFiles.size(); fileIdx++) {
      Path file = sortedFiles.get(fileIdx);
      try {
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        List<String> normalized = normalizeFileLines(splitLines(content));
        for (String block : createCodeBlocks(normalized)) {
          if (block.length() > 20) {
            out.add(new FileBlock(fileIdx, sha256Hex(block), block.length()));
          }
        }
      } catch (IOException | RuntimeException e) {
        LOG.debug("Failed to process {} for duplication detection: {}", file, e.getMessage());
      }
    }
    return out;
  }

  /** Port of {@code _normalize_file_lines}. */
  private static List<String> normalizeFileLines(List<String> lines) {
    List<String> out = new ArrayList<>();
    for (String line : lines) {
      String stripped = line.trim();
      // Python: `if stripped and not stripped.startswith("#")`. Java equivalent comment marker.
      if (!stripped.isEmpty() && !stripped.startsWith("//")) {
        String normalized = normalizeLine(stripped);
        if (!normalized.isEmpty()) {
          out.add(normalized);
        }
      }
    }
    return out;
  }

  /** Port of {@code _normalize_line}. */
  static String normalizeLine(String line) {
    String n = WHITESPACE.matcher(line).replaceAll("");
    n = FUNC_NAMES.matcher(n).replaceAll("FUNC");
    n = VAR_NAMES.matcher(n).replaceAll("VAR");
    return n;
  }

  /** Port of {@code _create_code_blocks}. */
  static List<String> createCodeBlocks(List<String> normalizedLines) {
    List<String> blocks = new ArrayList<>();
    int[] sizes = {3, 4, 5};
    for (int size : sizes) {
      for (int i = 0; i + size <= normalizedLines.size(); i++) {
        StringBuilder b = new StringBuilder();
        for (int j = 0; j < size; j++) {
          if (j > 0) {
            b.append('\n');
          }
          b.append(normalizedLines.get(i + j));
        }
        blocks.add(b.toString());
      }
    }
    return blocks;
  }

  /** Port of {@code _build_block_file_map}. */
  private static Map<String, Set<Integer>> buildBlockFileMap(List<FileBlock> fileBlocks) {
    Map<String, Set<Integer>> map = new HashMap<>();
    for (FileBlock fb : fileBlocks) {
      map.computeIfAbsent(fb.hash, k -> new HashSet<>()).add(fb.fileIdx);
    }
    return map;
  }

  /** Port of {@code _count_duplicate_blocks}. */
  private static int countDuplicateBlocks(Map<String, Set<Integer>> map) {
    int dup = 0;
    for (Set<Integer> indices : map.values()) {
      if (indices.size() > 1) {
        dup++;
      }
    }
    return dup;
  }

  /** Port of {@code _calculate_duplication_rate}. */
  private static double calculateDuplicationRate(
      int duplicateBlocks, Map<String, Set<Integer>> map, int numFiles) {
    double rate = map.isEmpty() ? 0.0 : ((double) duplicateBlocks / map.size()) * 100.0;
    if (numFiles > 10) {
      rate *= 1.5; // Boost for larger codebases — matches Python literal.
    }
    return Math.min(rate, 50.0);
  }

  /** Port of {@code _check_intra_file_duplication}. */
  private double checkIntraFileDuplication(Path file) {
    try {
      String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      List<String> lines = new ArrayList<>();
      for (String raw : splitLines(content)) {
        String s = raw.trim();
        if (!s.isEmpty()) {
          lines.add(s);
        }
      }
      if (lines.size() < 10) {
        return 0.0;
      }
      List<String> normalized = normalizeLinesForIntraFile(lines);
      int duplicated = countDuplicatedSequences(normalized);
      return calculateIntraFileRate(duplicated, lines.size());
    } catch (IOException | RuntimeException e) {
      LOG.debug("Failed to check intra-file duplication for {}: {}", file, e.getMessage());
      return 0.0;
    }
  }

  /** Port of {@code _normalize_lines_for_intra_file}. */
  private static List<String> normalizeLinesForIntraFile(List<String> lines) {
    List<String> out = new ArrayList<>(lines.size());
    for (String line : lines) {
      String n = INTRA_ATTR.matcher(line).replaceAll("VAR.ATTR");
      n = INTRA_ASSIGN.matcher(n).replaceAll("VAR =");
      out.add(n);
    }
    return out;
  }

  /** Port of {@code _count_duplicated_sequences}. */
  private static int countDuplicatedSequences(List<String> lines) {
    int duplicated = 0;
    int total = lines.size();
    int maxSeq = Math.min(6, total / 2);
    for (int seqLen = 3; seqLen < maxSeq; seqLen++) {
      duplicated += countSequencesOfLength(lines, seqLen);
    }
    return Math.min(duplicated, total);
  }

  /** Port of {@code _count_sequences_of_length}. */
  private static int countSequencesOfLength(List<String> lines, int seqLen) {
    int duplicated = 0;
    for (int i = 0; i + seqLen <= lines.size(); i++) {
      List<String> seq = lines.subList(i, i + seqLen);
      int count = countSequenceOccurrences(lines, seq, seqLen);
      if (count > 1) {
        duplicated += seqLen * (count - 1);
      }
    }
    return duplicated;
  }

  /** Port of {@code _count_sequence_occurrences}. */
  private static int countSequenceOccurrences(List<String> lines, List<String> seq, int seqLen) {
    int count = 0;
    for (int j = 0; j + seqLen <= lines.size(); j++) {
      if (lines.subList(j, j + seqLen).equals(seq)) {
        count++;
      }
    }
    return count;
  }

  /** Port of {@code _calculate_intra_file_duplication_rate}. */
  private static double calculateIntraFileRate(int duplicated, int total) {
    if (total == 0) {
      return 0.0;
    }
    return (((double) duplicated) / total) * 100.0;
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port of Python piecewise normalize():
    //   v <= 0  : 1.0
    //   v >= 50 : 0.0
    //   v <= 5  : 1.0 - v/50           [1.0 -> 0.9]
    //   v <= 15 : 0.9 - (v-5)/10 * 0.4 [0.9 -> 0.5]
    //   v <= 30 : 0.5 - (v-15)/15 * 0.4[0.5 -> 0.1]
    //   else    : max(0, 0.1 - (v-30)/20 * 0.1) [0.1 -> 0.0]
    if (value == null) {
      return 1.0;
    }
    double v = value;
    if (v <= 0.0) {
      return 1.0;
    }
    if (v >= 50.0) {
      return 0.0;
    }
    if (v <= 5.0) {
      return 1.0 - (v / 50.0);
    }
    if (v <= 15.0) {
      return 0.9 - ((v - 5.0) / 10.0) * 0.4;
    }
    if (v <= 30.0) {
      return 0.5 - ((v - 15.0) / 15.0) * 0.4;
    }
    return Math.max(0.0, 0.1 - ((v - 30.0) / 20.0) * 0.1);
  }

  @Override
  public double getWeight() {
    return 0.6;
  }

  @Override
  public String getName() {
    return "Code Duplication";
  }

  private static List<String> splitLines(String content) {
    List<String> out = new ArrayList<>();
    Collections.addAll(out, content.split("\\r?\\n", -1));
    return out;
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append(String.format("%02x", b & 0xff));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is mandated by every JRE; this branch is effectively unreachable.
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static final class FileBlock {
    final int fileIdx;
    final String hash;
    final int size;

    FileBlock(int fileIdx, String hash, int size) {
      this.fileIdx = fileIdx;
      this.hash = hash;
      this.size = size;
    }
  }
}
