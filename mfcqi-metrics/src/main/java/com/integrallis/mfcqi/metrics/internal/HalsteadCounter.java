package com.integrallis.mfcqi.metrics.internal;

import com.github.javaparser.JavaToken;
import com.github.javaparser.TokenRange;
import java.util.HashSet;
import java.util.Set;

/**
 * Computes Halstead Volume {@code V = (N1 + N2) * log2(n1 + n2)} for a parsed Java file.
 *
 * <p>Mirrors the role {@code radon.metrics.h_visit} plays for the Python reference's {@code
 * HalsteadComplexity.extract}. Radon's {@code HalsteadVisitor} only counts genuine operator nodes
 * (arithmetic, unary, boolean, comparison, augmented-assignment) as operators and {@code NAME} +
 * literals as operands — it does NOT count keywords ({@code if}/{@code for}/{@code return}/…) or
 * punctuation separators ({@code (} {@code )} {@code &#123;} {@code &#125;} {@code ,} {@code ;}
 * {@code .}). The equivalent classification for Java tokens is therefore:
 *
 * <ul>
 *   <li>Operands: {@code IDENTIFIER} + {@code LITERAL}
 *   <li>Operators: {@code OPERATOR} only
 * </ul>
 *
 * <p>Counting {@code KEYWORD} and {@code SEPARATOR} as operators (as an earlier port did) inflated
 * per-file volume ~15-20x versus radon, collapsing the {@code 1 - tanh(V/2500)} normalization curve
 * toward 0 for every real codebase. Whitespace, EOL, comments, keywords, separators, and EOF tokens
 * are not counted. Returns 0.0 for an empty file or when the vocabulary {@code n1 + n2} ≤ 1.
 */
public final class HalsteadCounter {

  /**
   * Empirical factor by which this token-based counter's volume exceeds radon's on equivalent code
   * (radon counts only operands inside arithmetic/boolean/comparison expressions; this counts all
   * identifiers/literals). Measured ~5x on the two functionally-equivalent sibling codebases (Java
   * mean per-file ≈1608 vs Python radon ≈322). Consumers that compare against radon-calibrated
   * formulas (e.g. the Maintainability Index's {@code ln(HV)} term) divide by this to get a
   * radon-scale volume; the standalone {@link HalsteadVolume} bakes the same factor into its tanh
   * divisor instead. See HalsteadVolume's javadoc for the calibration method.
   */
  public static final double RADON_VOLUME_SCALE = 5.0;

  private HalsteadCounter() {}

  /** Volume rescaled to radon's magnitude, for use in radon-calibrated formulas like the MI. */
  public static double radonScaledVolume(ParsedFile file) {
    return volume(file) / RADON_VOLUME_SCALE;
  }

  /** Compute volume for a parsed file. */
  public static double volume(ParsedFile file) {
    return file.compilationUnit().getTokenRange().map(HalsteadCounter::volume).orElse(0.0);
  }

  static double volume(TokenRange tokens) {
    Set<String> uniqueOperators = new HashSet<>();
    Set<String> uniqueOperands = new HashSet<>();
    long totalOperators = 0;
    long totalOperands = 0;

    for (JavaToken token : tokens) {
      JavaToken.Category category = token.getCategory();
      if (category == null) {
        continue;
      }
      String text = token.getText();
      if (text == null || text.isEmpty()) {
        continue;
      }
      switch (category) {
        case OPERATOR:
          // radon counts arithmetic/unary/boolean/comparison/augmented-assignment operators but
          // NOT plain assignment "=" (it has no visit_Assign). Plain "=" appears on every Java
          // declaration-with-initializer and assignment, so counting it inflates volume well above
          // the Python-calibrated curve. Compound assignments (+=, -=, …) are kept.
          if (text.equals("=")) {
            break;
          }
          uniqueOperators.add(text);
          totalOperators++;
          break;
        case IDENTIFIER:
        case LITERAL:
          uniqueOperands.add(text);
          totalOperands++;
          break;
        case KEYWORD:
        case SEPARATOR:
        case WHITESPACE_NO_EOL:
        case EOL:
        case COMMENT:
        default:
          // Not Halstead operators/operands — radon counts neither keywords nor separators.
          break;
      }
    }

    int vocabulary = uniqueOperators.size() + uniqueOperands.size();
    long length = totalOperators + totalOperands;
    if (vocabulary <= 1 || length <= 0) {
      return 0.0;
    }
    return length * (Math.log(vocabulary) / Math.log(2.0));
  }
}
