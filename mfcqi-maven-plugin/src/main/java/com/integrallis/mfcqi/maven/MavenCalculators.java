package com.integrallis.mfcqi.maven;

import com.integrallis.mfcqi.core.MFCQICalculator;
import com.integrallis.mfcqi.engine.MFCQIDefaults;
import com.integrallis.mfcqi.security.bytecode.BytecodeSecurityScanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;

/**
 * Builds the calculator for a Mojo. When bytecode security is enabled and the project has been
 * compiled ({@code target/classes}), the source security metric is replaced by a real
 * SpotBugs+FindSecBugs bytecode scanner (Java and Kotlin); otherwise the default source set is
 * used.
 */
final class MavenCalculators {

  private MavenCalculators() {}

  static MFCQICalculator build(
      Path path, int parallelism, boolean bytecodeSecurity, MavenProject project) {
    List<Path> classDirs = new ArrayList<>();
    if (project != null && project.getBuild() != null) {
      Path output = Paths.get(project.getBuild().getOutputDirectory());
      if (Files.isDirectory(output)) {
        classDirs.add(output);
      }
    }
    if (bytecodeSecurity && !classDirs.isEmpty()) {
      return MFCQIDefaults.calculatorFor(
          path, parallelism, new BytecodeSecurityScanner(classDirs, compileClasspath(project)));
    }
    return MFCQIDefaults.calculatorFor(path, parallelism);
  }

  private static List<Path> compileClasspath(MavenProject project) {
    List<Path> classpath = new ArrayList<>();
    try {
      for (String entry : project.getCompileClasspathElements()) {
        Path path = Paths.get(entry);
        if (Files.exists(path)) {
          classpath.add(path);
        }
      }
    } catch (DependencyResolutionRequiredException ignored) {
      // Analysis still runs; SpotBugs just resolves fewer library types.
    }
    return classpath;
  }
}
