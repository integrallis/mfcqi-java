package com.integrallis.mfcqi.gradle;

import com.integrallis.mfcqi.core.MFCQICalculator;
import com.integrallis.mfcqi.engine.MFCQIDefaults;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** {@code mfcqiAnalyze} — computes the MFCQI score and per-metric breakdown. */
@DisableCachingByDefault(because = "MFCQI analysis scans the source tree on each run")
public abstract class MfcqiAnalyzeTask extends DefaultTask {

  @Internal
  public abstract DirectoryProperty getSource();

  @Input
  public abstract Property<Integer> getParallelism();

  @Optional
  @OutputFile
  public abstract RegularFileProperty getJsonReport();

  @TaskAction
  public void run() {
    Path path = getSource().get().getAsFile().toPath();
    MFCQICalculator calculator = MFCQIDefaults.calculatorFor(path, getParallelism().get());
    Map<String, Double> detailed = calculator.detailedMetrics(path);
    double score = detailed.getOrDefault("mfcqi_score", 0.0);

    getLogger().lifecycle("MFCQI score: {}", String.format(Locale.ROOT, "%.3f", score));
    detailed.forEach(
        (name, value) -> {
          if (!"mfcqi_score".equals(name)) {
            getLogger().lifecycle(String.format(Locale.ROOT, "  %-30s %.3f", name, value));
          }
        });

    if (getJsonReport().isPresent()) {
      Path out = getJsonReport().get().getAsFile().toPath();
      try {
        if (out.getParent() != null) {
          Files.createDirectories(out.getParent());
        }
        Files.writeString(
            out, com.integrallis.mfcqi.engine.MetricJson.of(detailed), StandardCharsets.UTF_8);
        getLogger().lifecycle("MFCQI JSON report written to {}", out);
      } catch (IOException e) {
        throw new GradleException("Failed to write MFCQI JSON report to " + out, e);
      }
    }
  }
}
