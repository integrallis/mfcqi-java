package com.integrallis.mfcqi.metrics.internal;

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
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses Java source files into JavaParser {@link CompilationUnit} instances. Mirrors the role
 * Python metrics play when they call {@code radon.complexity.cc_visit(code)} or {@code
 * ast.parse(content)} on each file returned by {@code get_python_files} — see the per-metric Python
 * sources in {@code mfcqi/metrics/}.
 *
 * <p>Files that fail to parse are silently skipped, matching the {@code except (SyntaxError,
 * UnicodeDecodeError): continue} pattern used throughout the Python reference.
 */
public final class ParsedSources {

  private static final Logger LOG = LoggerFactory.getLogger(ParsedSources.class);

  private ParsedSources() {}

  /** Parse every {@code .java} file under {@code codebase}, optionally skipping test files. */
  public static List<ParsedFile> parseAll(Path codebase, boolean excludeTests) {
    if (codebase == null) {
      return Collections.emptyList();
    }
    List<Path> files = JavaSourceFiles.find(codebase, excludeTests);
    List<ParsedFile> parsed = new ArrayList<>(files.size());
    for (Path file : files) {
      parseOne(file).ifPresent(parsed::add);
    }
    return parsed;
  }

  /** Convenience: include tests. */
  public static List<ParsedFile> parseAll(Path codebase) {
    return parseAll(codebase, false);
  }

  /** Parse a single file. Returns empty if reading or parsing fails. */
  public static java.util.Optional<ParsedFile> parseOne(Path file) {
    Objects.requireNonNull(file, "file");
    try {
      String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      CompilationUnit cu = StaticJavaParser.parse(source);
      return java.util.Optional.of(new ParsedFile(file, source, cu));
    } catch (IOException | RuntimeException e) {
      LOG.debug("Skipping unparseable file {}: {}", file, e.getMessage());
      return java.util.Optional.empty();
    }
  }
}
