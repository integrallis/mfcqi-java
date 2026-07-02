package com.integrallis.mfcqi.gradle;

import com.integrallis.mfcqi.badge.BadgeGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** {@code mfcqiBadge} — writes a shields.io endpoint badge JSON from the MFCQI score. */
@DisableCachingByDefault(because = "MFCQI analysis scans the source tree on each run")
public abstract class MfcqiBadgeTask extends DefaultTask {

  @Internal
  public abstract DirectoryProperty getSource();

  @Input
  public abstract Property<Integer> getParallelism();

  @OutputFile
  public abstract RegularFileProperty getBadgeFile();

  @Internal
  public abstract ConfigurableFileCollection getClassDirs();

  @Internal
  public abstract ConfigurableFileCollection getAnalysisClasspath();

  @Input
  public abstract Property<Boolean> getBytecodeSecurity();

  @TaskAction
  public void run() {
    Path path = getSource().get().getAsFile().toPath();
    double score =
        MfcqiCalculators.build(
                path,
                getParallelism().get(),
                getBytecodeSecurity().getOrElse(true),
                getClassDirs(),
                getAnalysisClasspath())
            .calculate(path);
    Path out = getBadgeFile().get().getAsFile().toPath();
    try {
      if (out.getParent() != null) {
        Files.createDirectories(out.getParent());
      }
      Files.writeString(out, BadgeGenerator.endpointJson(score), StandardCharsets.UTF_8);
      getLogger()
          .lifecycle(
              "MFCQI badge ({}) written to {}", String.format(Locale.ROOT, "%.3f", score), out);
    } catch (IOException e) {
      throw new GradleException("Failed to write MFCQI badge to " + out, e);
    }
  }
}
