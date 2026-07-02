package com.integrallis.mfcqi.security.bytecode;

import com.integrallis.mfcqi.security.SecurityFinding;
import com.integrallis.mfcqi.security.SecurityScanner;
import edu.umd.cs.findbugs.BugCollectionBugReporter;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.FindBugs2;
import edu.umd.cs.findbugs.Plugin;
import edu.umd.cs.findbugs.Priorities;
import edu.umd.cs.findbugs.Project;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.config.UserPreferences;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Real SAST security scanner: runs SpotBugs with the FindSecBugs plugin over compiled bytecode and
 * maps its SECURITY-category findings onto {@link SecurityFinding}s so {@link
 * com.integrallis.mfcqi.security.SecurityMetric} scores them exactly like the source scanner.
 *
 * <p>Analyzes bytecode, so it needs compiled class directories — which the Gradle/Maven plugins
 * have but the source-only CLI does not. SpotBugs is not native-image friendly, so this scanner is
 * never on the CLI / GraalVM path.
 */
public final class BytecodeSecurityScanner implements SecurityScanner {

  private final List<Path> classDirs;
  private final List<Path> auxClasspath;

  /**
   * @param classDirs compiled class directories to analyze (e.g. {@code build/classes/java/main},
   *     {@code build/classes/kotlin/main})
   * @param auxClasspath dependency jars/dirs used to resolve types during analysis
   */
  public BytecodeSecurityScanner(List<Path> classDirs, List<Path> auxClasspath) {
    this.classDirs = List.copyOf(classDirs);
    this.auxClasspath = List.copyOf(auxClasspath);
  }

  @Override
  public List<SecurityFinding> scan(Path codebase) {
    List<SecurityFinding> findings = new ArrayList<>();
    if (classDirs.isEmpty()) {
      return findings;
    }
    Project project = new Project();
    for (Path dir : classDirs) {
      project.addFile(dir.toAbsolutePath().toString());
    }
    for (Path entry : auxClasspath) {
      project.addAuxClasspathEntry(entry.toAbsolutePath().toString());
    }

    loadFindSecBugsPlugin(project);
    DetectorFactoryCollection detectors = DetectorFactoryCollection.instance();
    BugCollectionBugReporter reporter = new BugCollectionBugReporter(project);
    reporter.setPriorityThreshold(Priorities.LOW_PRIORITY);

    try (FindBugs2 findBugs = new FindBugs2()) {
      findBugs.setProject(project);
      findBugs.setDetectorFactoryCollection(detectors);
      findBugs.setBugReporter(reporter);
      findBugs.setUserPreferences(UserPreferences.createDefaultUserPreferences());
      findBugs.execute();
    } catch (Exception e) {
      throw new IllegalStateException("SpotBugs/FindSecBugs analysis failed", e);
    }

    for (BugInstance bug : reporter.getBugCollection()) {
      if (!"SECURITY".equals(bug.getBugPattern().getCategory())) {
        continue;
      }
      SourceLineAnnotation line = bug.getPrimarySourceLineAnnotation();
      int lineNumber = line != null ? Math.max(0, line.getStartLine()) : 0;
      findings.add(
          new SecurityFinding(
              codebase,
              lineNumber,
              bug.getType(),
              bug.getBugPattern().getShortDescription(),
              severityOf(bug.getPriority()),
              confidenceOf(bug.getPriority()),
              bug.getBugPattern().getShortDescription(),
              cweOf(bug)));
    }
    return findings;
  }

  /**
   * SpotBugs does not auto-discover FindSecBugs from an arbitrary (Gradle/test) classloader, so we
   * locate its jar and register it as a custom plugin for this analysis. Idempotent: a second load
   * throws and is ignored.
   */
  private static void loadFindSecBugsPlugin(Project project) {
    URL jar = findFindSecBugsJar();
    if (jar == null) {
      return;
    }
    try {
      Plugin.loadCustomPlugin(jar, project);
    } catch (RuntimeException | edu.umd.cs.findbugs.PluginException e) {
      // Already registered, or another loader owns it — the detectors are available either way.
    }
  }

  private static URL findFindSecBugsJar() {
    // Preferred: the jar that owns a stable FindSecBugs class (works under any classloader).
    try {
      Class<?> anchor = Class.forName("com.h3xstream.findsecbugs.FindSecBugsGlobalConfig");
      return anchor.getProtectionDomain().getCodeSource().getLocation();
    } catch (Throwable ignored) {
      // Fall back to scanning the system classpath.
    }
    String classpath = System.getProperty("java.class.path", "");
    for (String entry : classpath.split(File.pathSeparator)) {
      if (entry.contains("findsecbugs")) {
        try {
          return Paths.get(entry).toUri().toURL();
        } catch (Exception ignored) {
          return null;
        }
      }
    }
    return null;
  }

  private static SecurityFinding.Severity severityOf(int priority) {
    if (priority <= Priorities.HIGH_PRIORITY) {
      return SecurityFinding.Severity.HIGH;
    }
    return priority == Priorities.NORMAL_PRIORITY
        ? SecurityFinding.Severity.MEDIUM
        : SecurityFinding.Severity.LOW;
  }

  private static SecurityFinding.Confidence confidenceOf(int priority) {
    if (priority <= Priorities.HIGH_PRIORITY) {
      return SecurityFinding.Confidence.HIGH;
    }
    return priority == Priorities.NORMAL_PRIORITY
        ? SecurityFinding.Confidence.MEDIUM
        : SecurityFinding.Confidence.LOW;
  }

  private static String cweOf(BugInstance bug) {
    int cwe = bug.getBugPattern().getCWEid();
    return cwe > 0 ? "CWE-" + cwe : "CWE-0";
  }
}
