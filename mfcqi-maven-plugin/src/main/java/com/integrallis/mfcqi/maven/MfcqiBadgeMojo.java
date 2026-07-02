package com.integrallis.mfcqi.maven;

import com.integrallis.mfcqi.badge.BadgeGenerator;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** {@code mfcqi:badge} — writes a shields.io endpoint badge JSON from the MFCQI score. */
@Mojo(name = "badge", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class MfcqiBadgeMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project.basedir}", property = "mfcqi.source")
  private File source;

  @Parameter(property = "mfcqi.parallelism", defaultValue = "1")
  private int parallelism;

  @Parameter(
      property = "mfcqi.badgeFile",
      defaultValue = "${project.basedir}/.github/badges/mfcqi.json")
  private File badgeFile;

  /** The current Maven project — used to locate compiled classes for bytecode security. */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  /** Use real SpotBugs+FindSecBugs bytecode SAST when the project is compiled. */
  @Parameter(property = "mfcqi.bytecodeSecurity", defaultValue = "true")
  private boolean bytecodeSecurity;

  @Override
  public void execute() throws MojoExecutionException {
    Path path = source.toPath();
    double score =
        MavenCalculators.build(path, Math.max(1, parallelism), bytecodeSecurity, project)
            .calculate(path);
    Path out = badgeFile.toPath();
    try {
      if (out.getParent() != null) {
        Files.createDirectories(out.getParent());
      }
      Files.writeString(out, BadgeGenerator.endpointJson(score), StandardCharsets.UTF_8);
      getLog().info(String.format(Locale.ROOT, "MFCQI badge (%.3f) written to %s", score, out));
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to write MFCQI badge to " + out, e);
    }
  }
}
