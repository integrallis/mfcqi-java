package com.integrallis.mfcqi.metrics.internal;

import com.github.javaparser.Position;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * File-level helpers for the Maintainability Index port. Produces SLOC and comment-line counts
 * roughly equivalent to radon's {@code raw.sloc} / {@code raw.comments} (which are inputs to {@code
 * mi_compute} via {@code mi_parameters}). Per-file CC and HV come from {@link CyclomaticUnits} and
 * {@link HalsteadCounter} respectively.
 *
 * <p>Java equivalence note: radon's "logical LOC" (statement count) is not directly available from
 * JavaParser without extra walking; this port uses physical SLOC (non-blank source lines) as the
 * analog, since the radon formula uses LLOC where the divergence is typically small for idiomatic
 * code. The MI formula is otherwise translated verbatim.
 */
public final class FileMetrics {

  private FileMetrics() {}

  /** Number of non-blank source lines in {@code source}. */
  public static int sourceLines(String source) {
    if (source == null || source.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (String line : source.split("\\r?\\n", -1)) {
      if (!line.trim().isEmpty()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Number of distinct source lines covered by Javadoc, block, or line comments in {@code file} —
   * equivalent to {@code raw.comments + raw.multi} in radon's {@code mi_parameters} when {@code
   * count_multi=True} (the default for {@code multi=False} in {@code mi_visit} excludes triple-
   * string docstrings, which Java doesn't have, so all Java comments count uniformly).
   */
  public static int commentLines(ParsedFile file) {
    Set<Integer> lines = new HashSet<>();
    for (Comment c : file.compilationUnit().getAllContainedComments()) {
      addRange(c, lines);
    }
    // Orphan comments (e.g., trailing) are also exposed via getAllContainedComments(), so we
    // additionally pick up any comments attached at the compilation-unit level.
    file.compilationUnit().getComment().ifPresent(c -> addRange(c, lines));
    return lines.size();
  }

  /** Total cyclomatic complexity across every executable unit in the file. */
  public static int totalCyclomaticComplexity(ParsedFile file) {
    int total = 0;
    for (int cc : CyclomaticUnits.complexitiesIn(file)) {
      total += cc;
    }
    return total;
  }

  private static void addRange(NodeWithRange<?> node, Set<Integer> out) {
    Optional<Position> begin = node.getRange().map(r -> r.begin);
    Optional<Position> end = node.getRange().map(r -> r.end);
    if (!begin.isPresent() || !end.isPresent()) {
      return;
    }
    for (int line = begin.get().line; line <= end.get().line; line++) {
      out.add(line);
    }
  }
}
