package com.integrallis.mfcqi.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * First-party MFCQI Gradle plugin. Applying it registers three tasks:
 *
 * <ul>
 *   <li>{@code mfcqiAnalyze} — compute the MFCQI score and per-metric breakdown
 *   <li>{@code mfcqiBadge} — write a shields.io endpoint badge JSON
 *   <li>{@code mfcqiGate} — evaluate {@code .mfcqi.yaml} quality gates and fail the build on
 *       failure
 * </ul>
 *
 * <pre>plugins { id("com.integrallis.mfcqi") }</pre>
 */
public class MfcqiPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    MfcqiExtension ext = project.getExtensions().create("mfcqi", MfcqiExtension.class);
    ext.getSource().convention(project.getLayout().getProjectDirectory());
    ext.getParallelism().convention(Runtime.getRuntime().availableProcessors());
    ext.getBadgeFile()
        .convention(project.getLayout().getProjectDirectory().file(".github/badges/mfcqi.json"));
    ext.getFailOnGate().convention(true);

    project
        .getTasks()
        .register(
            "mfcqiAnalyze",
            MfcqiAnalyzeTask.class,
            task -> {
              task.setGroup("verification");
              task.setDescription("Compute the MFCQI code-quality score and per-metric breakdown.");
              task.getSource().set(ext.getSource());
              task.getParallelism().set(ext.getParallelism());
              task.getJsonReport().set(ext.getJsonReport());
              task.getOutputs().upToDateWhen(t -> false);
            });

    project
        .getTasks()
        .register(
            "mfcqiBadge",
            MfcqiBadgeTask.class,
            task -> {
              task.setGroup("verification");
              task.setDescription(
                  "Generate a shields.io endpoint badge JSON from the MFCQI score.");
              task.getSource().set(ext.getSource());
              task.getParallelism().set(ext.getParallelism());
              task.getBadgeFile().set(ext.getBadgeFile());
              task.getOutputs().upToDateWhen(t -> false);
            });

    project
        .getTasks()
        .register(
            "mfcqiGate",
            MfcqiGateTask.class,
            task -> {
              task.setGroup("verification");
              task.setDescription(
                  "Evaluate .mfcqi.yaml quality gates; fails the build on failure.");
              task.getSource().set(ext.getSource());
              task.getParallelism().set(ext.getParallelism());
              task.getGateFile().set(ext.getGateFile());
              task.getFailOnGate().set(ext.getFailOnGate());
              task.getOutputs().upToDateWhen(t -> false);
            });
  }
}
