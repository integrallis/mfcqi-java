package com.integrallis.mfcqi.smells.internal;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.integrallis.mfcqi.core.JavaSourceFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Walks a codebase and yields parsed {@link CompilationUnit}s. Mirrors the role of {@code
 * mfcqi.core.file_utils.get_python_files} + {@code ast.parse} in the Python smell detectors. Files
 * that fail to parse are silently skipped.
 */
public final class SourceTrees {

  private static final Logger LOG = LoggerFactory.getLogger(SourceTrees.class);

  private SourceTrees() {}

  /** Parse every {@code .java} file under {@code root}. */
  public static List<ParsedSource> parseAll(Path root) {
    if (root == null) {
      return Collections.emptyList();
    }
    List<ParsedSource> out = new ArrayList<>();
    for (Path file : JavaSourceFiles.findAll(root)) {
      parseOne(file).ifPresent(out::add);
    }
    return out;
  }

  /** Parse a single file. */
  public static Optional<ParsedSource> parseOne(Path file) {
    try {
      String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      CompilationUnit cu = StaticJavaParser.parse(source);
      return Optional.of(new ParsedSource(file, source, cu));
    } catch (IOException | RuntimeException e) {
      LOG.debug("Skipping unparseable file {}: {}", file, e.getMessage());
      return Optional.empty();
    }
  }

  /** Path + raw text + parsed CompilationUnit triple. */
  public static final class ParsedSource {
    private final Path path;
    private final String source;
    private final CompilationUnit compilationUnit;

    ParsedSource(Path path, String source, CompilationUnit compilationUnit) {
      this.path = path;
      this.source = source;
      this.compilationUnit = compilationUnit;
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
}
