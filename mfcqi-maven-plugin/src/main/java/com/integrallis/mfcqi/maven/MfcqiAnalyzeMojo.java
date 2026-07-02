package com.integrallis.mfcqi.maven;

import com.integrallis.mfcqi.core.MFCQICalculator;
import com.integrallis.mfcqi.engine.MetricJson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** {@code mfcqi:analyze} — computes the MFCQI score and per-metric breakdown. */
@Mojo(
    name = "analyze",
    defaultPhase = LifecyclePhase.VERIFY,
    threadSafe = true,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class MfcqiAnalyzeMojo extends AbstractMojo {

  /** Source root to analyze. Defaults to the project base directory. */
  @Parameter(defaultValue = "${project.basedir}", property = "mfcqi.source")
  private File source;

  /** Number of metric workers. */
  @Parameter(property = "mfcqi.parallelism", defaultValue = "1")
  private int parallelism;

  /** Optional JSON report destination. */
  @Parameter(property = "mfcqi.jsonReport")
  private File jsonReport;

  /** The current Maven project — used to locate compiled classes for bytecode security. */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  /** Use real SpotBugs+FindSecBugs bytecode SAST when the project is compiled. */
  @Parameter(property = "mfcqi.bytecodeSecurity", defaultValue = "true")
  private boolean bytecodeSecurity;

  @Override
  public void execute() throws MojoExecutionException {
    Path path = source.toPath();
    MFCQICalculator calculator =
        MavenCalculators.build(path, Math.max(1, parallelism), bytecodeSecurity, project);
    Map<String, Double> detailed = calculator.detailedMetrics(path);
    double score = detailed.getOrDefault("mfcqi_score", 0.0);

    getLog().info(String.format(Locale.ROOT, "MFCQI score: %.3f", score));
    detailed.forEach(
        (name, value) -> {
          if (!"mfcqi_score".equals(name)) {
            getLog().info(String.format(Locale.ROOT, "  %-30s %.3f", name, value));
          }
        });

    if (jsonReport != null) {
      Path out = jsonReport.toPath();
      try {
        if (out.getParent() != null) {
          Files.createDirectories(out.getParent());
        }
        Files.writeString(out, MetricJson.of(detailed), StandardCharsets.UTF_8);
        getLog().info("MFCQI JSON report written to " + out);
      } catch (IOException e) {
        throw new MojoExecutionException("Failed to write MFCQI JSON report to " + out, e);
      }
    }
  }
}
