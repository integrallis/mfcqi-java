package com.integrallis.mfcqi.metrics.internal;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A successfully parsed Java source file: the original text (for token/line counts), the parsed
 * {@link CompilationUnit}, and the file path (for diagnostics).
 */
public final class ParsedFile {

  private final Path path;
  private final String source;
  private final CompilationUnit compilationUnit;

  public ParsedFile(Path path, String source, CompilationUnit compilationUnit) {
    this.path = Objects.requireNonNull(path, "path");
    this.source = Objects.requireNonNull(source, "source");
    this.compilationUnit = Objects.requireNonNull(compilationUnit, "compilationUnit");
  }

  public Path path() {
    return path;
  }

  public String source() {
    return source;
  }

  public CompilationUnit compilationUnit() {
    return compilationUnit;
  }
}
