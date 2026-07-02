package com.integrallis.mfcqi.gradle;

import com.integrallis.mfcqi.core.MFCQICalculator;
import com.integrallis.mfcqi.engine.MFCQIDefaults;
import com.integrallis.mfcqi.security.bytecode.BytecodeSecurityScanner;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.file.FileCollection;

/**
 * Builds the calculator for a task. When bytecode security is enabled and compiled class
 * directories are available, the source security metric is replaced by a real SpotBugs+FindSecBugs
 * bytecode scanner (for both Java and Kotlin); otherwise the default source metric set is used.
 */
final class MfcqiCalculators {

  private MfcqiCalculators() {}

  static MFCQICalculator build(
      Path path,
      int parallelism,
      boolean bytecodeSecurity,
      FileCollection classDirs,
      FileCollection analysisClasspath) {
    List<Path> dirs = directories(classDirs);
    if (bytecodeSecurity && !dirs.isEmpty()) {
      return MFCQIDefaults.calculatorFor(
          path, parallelism, new BytecodeSecurityScanner(dirs, existing(analysisClasspath)));
    }
    return MFCQIDefaults.calculatorFor(path, parallelism);
  }

  private static List<Path> directories(FileCollection files) {
    List<Path> out = new ArrayList<>();
    if (files != null) {
      for (File file : files.getFiles()) {
        if (file.isDirectory()) {
          out.add(file.toPath());
        }
      }
    }
    return out;
  }

  private static List<Path> existing(FileCollection files) {
    List<Path> out = new ArrayList<>();
    if (files != null) {
      for (File file : files.getFiles()) {
        if (file.exists()) {
          out.add(file.toPath());
        }
      }
    }
    return out;
  }
}
