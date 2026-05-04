package com.integrallis.mfcqi.metrics.internal;

import com.github.javaparser.JavaToken;
import com.github.javaparser.TokenRange;
import java.util.HashSet;
import java.util.Set;

/**
 * Computes Halstead Volume {@code V = (N1 + N2) * log2(n1 + n2)} for a parsed Java file.
 *
 * <p>Mirrors the role {@code radon.metrics.h_visit} plays for the Python reference's {@code
 * HalsteadComplexity.extract}. Radon classifies Python tokens into operators (OP + most keywords)
 * and operands (NAME + literals); the equivalent classification used here for Java tokens is:
 *
 * <ul>
 *   <li>Operands: {@code IDENTIFIER} + {@code LITERAL}
 *   <li>Operators: {@code OPERATOR} + {@code KEYWORD} + {@code SEPARATOR}
 * </ul>
 *
 * Whitespace, EOL, comments, and EOF tokens are not counted. Returns 0.0 for an empty file or when
 * the vocabulary {@code n1 + n2} ≤ 1.
 */
public final class HalsteadCounter {

  private HalsteadCounter() {}

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
        case KEYWORD:
        case SEPARATOR:
          uniqueOperators.add(text);
          totalOperators++;
          break;
        case IDENTIFIER:
        case LITERAL:
          uniqueOperands.add(text);
          totalOperands++;
          break;
        case WHITESPACE_NO_EOL:
        case EOL:
        case COMMENT:
        default:
          // Ignore.
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
