package com.integrallis.mfcqi.maven;

import com.integrallis.mfcqi.badge.BadgeGenerator;
import com.integrallis.mfcqi.engine.MFCQIDefaults;
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

/** {@code mfcqi:badge} — writes a shields.io endpoint badge JSON from the MFCQI score. */
@Mojo(name = "badge", threadSafe = true)
public class MfcqiBadgeMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project.basedir}", property = "mfcqi.source")
  private File source;

  @Parameter(property = "mfcqi.parallelism", defaultValue = "1")
  private int parallelism;

  @Parameter(
      property = "mfcqi.badgeFile",
      defaultValue = "${project.basedir}/.github/badges/mfcqi.json")
  private File badgeFile;

  @Override
  public void execute() throws MojoExecutionException {
    Path path = source.toPath();
    double score = MFCQIDefaults.calculatorFor(path, Math.max(1, parallelism)).calculate(path);
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
