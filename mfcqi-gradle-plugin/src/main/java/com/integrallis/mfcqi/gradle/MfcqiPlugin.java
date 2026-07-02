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
    ext.getBytecodeSecurity().convention(true);

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
              task.getBytecodeSecurity().set(ext.getBytecodeSecurity());
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
              task.getBytecodeSecurity().set(ext.getBytecodeSecurity());
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
              task.getBytecodeSecurity().set(ext.getBytecodeSecurity());
              task.getOutputs().upToDateWhen(t -> false);
            });

    // When a JVM plugin is applied, feed compiled classes (Java + Kotlin) to the tasks so the
    // bytecode security scanner can run, and make analysis depend on compilation.
    project
        .getPlugins()
        .withType(
            org.gradle.api.plugins.JavaBasePlugin.class,
            plugin -> {
              org.gradle.api.tasks.SourceSetContainer sourceSets =
                  project.getExtensions().getByType(org.gradle.api.tasks.SourceSetContainer.class);
              org.gradle.api.tasks.SourceSet main = sourceSets.findByName("main");
              if (main == null) {
                return;
              }
              wireClasses(project, "mfcqiAnalyze", MfcqiAnalyzeTask.class, main);
              wireClasses(project, "mfcqiBadge", MfcqiBadgeTask.class, main);
              wireClasses(project, "mfcqiGate", MfcqiGateTask.class, main);
            });
  }

  private static <T extends org.gradle.api.Task> void wireClasses(
      Project project, String name, Class<T> type, org.gradle.api.tasks.SourceSet main) {
    project
        .getTasks()
        .named(name, type)
        .configure(
            task -> {
              org.gradle.api.file.ConfigurableFileCollection classDirs = classDirs(task);
              org.gradle.api.file.ConfigurableFileCollection classpath = analysisClasspath(task);
              classDirs.from(main.getOutput().getClassesDirs());
              classpath.from(main.getCompileClasspath());
              task.dependsOn(main.getClassesTaskName());
            });
  }

  private static org.gradle.api.file.ConfigurableFileCollection classDirs(
      org.gradle.api.Task task) {
    if (task instanceof MfcqiAnalyzeTask) return ((MfcqiAnalyzeTask) task).getClassDirs();
    if (task instanceof MfcqiBadgeTask) return ((MfcqiBadgeTask) task).getClassDirs();
    return ((MfcqiGateTask) task).getClassDirs();
  }

  private static org.gradle.api.file.ConfigurableFileCollection analysisClasspath(
      org.gradle.api.Task task) {
    if (task instanceof MfcqiAnalyzeTask) return ((MfcqiAnalyzeTask) task).getAnalysisClasspath();
    if (task instanceof MfcqiBadgeTask) return ((MfcqiBadgeTask) task).getAnalysisClasspath();
    return ((MfcqiGateTask) task).getAnalysisClasspath();
  }
}
