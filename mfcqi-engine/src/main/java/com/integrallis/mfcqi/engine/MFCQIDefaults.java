package com.integrallis.mfcqi.engine;

import com.integrallis.mfcqi.core.JavaSourceFiles;
import com.integrallis.mfcqi.core.MFCQICalculator;
import com.integrallis.mfcqi.core.Metric;
import com.integrallis.mfcqi.depssecurity.DependencySecurityMetric;
import com.integrallis.mfcqi.duplication.CodeDuplication;
import com.integrallis.mfcqi.kotlin.KotlinMetrics;
import com.integrallis.mfcqi.metrics.CognitiveComplexity;
import com.integrallis.mfcqi.metrics.CouplingBetweenObjects;
import com.integrallis.mfcqi.metrics.CyclomaticComplexity;
import com.integrallis.mfcqi.metrics.DITMetric;
import com.integrallis.mfcqi.metrics.DocumentationCoverage;
import com.integrallis.mfcqi.metrics.HalsteadVolume;
import com.integrallis.mfcqi.metrics.LackOfCohesionOfMethods;
import com.integrallis.mfcqi.metrics.MHFMetric;
import com.integrallis.mfcqi.metrics.MaintainabilityIndex;
import com.integrallis.mfcqi.metrics.RFCMetric;
import com.integrallis.mfcqi.secrets.SecretsExposureMetric;
import com.integrallis.mfcqi.security.SecurityMetric;
import com.integrallis.mfcqi.security.SecurityScanner;
import com.integrallis.mfcqi.smells.CodeSmellDensity;
import java.nio.file.Path;
import java.util.List;

/**
 * Factory that wires every metric into a default-configured {@link MFCQICalculator}. Java is an
 * object-oriented language by construction, so all 15 metrics — code-quality, OO design, and
 * security — are evaluated on every analysis.
 *
 * <ul>
 *   <li>Code quality: CyclomaticComplexity, CognitiveComplexity, HalsteadVolume,
 *       MaintainabilityIndex, CodeDuplication, DocumentationCoverage, CodeSmellDensity (with its
 *       default detectors)
 *   <li>OO design: RFC, DIT, MHF, CBO, LCOM
 *   <li>Security: SecurityMetric (SAST), DependencySecurityMetric (SCA), SecretsExposureMetric
 * </ul>
 */
public final class MFCQIDefaults {

  private MFCQIDefaults() {}

  /** Build a {@link MFCQICalculator} with the default metric registry. */
  public static MFCQICalculator calculator() {
    return calculator(1);
  }

  /** Build a {@link MFCQICalculator} with the default metric registry and worker count. */
  public static MFCQICalculator calculator(int parallelism) {
    return javaBuilder(parallelism).build();
  }

  /** Build a {@link MFCQICalculator} for Kotlin codebases with the same metric contract as Java. */
  public static MFCQICalculator kotlinCalculator() {
    return kotlinCalculator(1);
  }

  /** Build a Kotlin {@link MFCQICalculator} with the requested worker count. */
  public static MFCQICalculator kotlinCalculator(int parallelism) {
    return kotlinBuilder(parallelism).build();
  }

  /** Build a calculator that combines corresponding Java and Kotlin source metrics. */
  public static MFCQICalculator mixedCalculator() {
    return mixedCalculator(1);
  }

  /** Build a mixed-language calculator with the requested worker count. */
  public static MFCQICalculator mixedCalculator(int parallelism) {
    return mixedBuilder(parallelism).build();
  }

  /** Auto-select Java, Kotlin, or mixed analysis from source files under {@code path}. */
  public static MFCQICalculator calculatorFor(Path path, int parallelism) {
    return builderFor(path, parallelism).build();
  }

  /** Auto-select Java, Kotlin, or mixed analysis with serial metric execution. */
  public static MFCQICalculator calculatorFor(Path path) {
    return calculatorFor(path, 1);
  }

  /**
   * Auto-select a calculator, replacing the source-based security metric with the given scanner
   * (e.g. a bytecode SpotBugs+FindSecBugs scanner supplied by a build plugin, which has compiled
   * classes). A {@code null} scanner leaves the default source security metric in place.
   */
  public static MFCQICalculator calculatorFor(
      Path path, int parallelism, SecurityScanner securityScanner) {
    MFCQICalculator.Builder builder = builderFor(path, parallelism);
    if (securityScanner != null) {
      builder.addMetric(new SecurityMetric(securityScanner, SecurityMetric.DEFAULT_THRESHOLD));
    }
    return builder.build();
  }

  private static MFCQICalculator.Builder javaBuilder(int parallelism) {
    MFCQICalculator.Builder builder = MFCQICalculator.builder().parallelism(parallelism);
    javaSourceMetrics().forEach(builder::addMetric);
    return addSharedMetrics(builder);
  }

  private static MFCQICalculator.Builder kotlinBuilder(int parallelism) {
    MFCQICalculator.Builder builder = MFCQICalculator.builder().parallelism(parallelism);
    KotlinMetrics.all().forEach(builder::addMetric);
    return addSharedMetrics(builder).analyzableSource(KotlinMetrics::hasSource);
  }

  private static MFCQICalculator.Builder mixedBuilder(int parallelism) {
    List<Metric<?>> javaMetrics = javaSourceMetrics();
    List<Metric<?>> kotlinMetrics = KotlinMetrics.all();
    if (javaMetrics.size() != kotlinMetrics.size()) {
      throw new IllegalStateException("Java and Kotlin metric registries are not aligned");
    }
    MFCQICalculator.Builder builder = MFCQICalculator.builder().parallelism(parallelism);
    for (int i = 0; i < javaMetrics.size(); i++) {
      Metric<?> javaMetric = javaMetrics.get(i);
      Metric<?> kotlinMetric = kotlinMetrics.get(i);
      if (!javaMetric.getName().equals(kotlinMetric.getName())) {
        throw new IllegalStateException(
            "Metric registry mismatch: " + javaMetric.getName() + " != " + kotlinMetric.getName());
      }
      builder.addMetric(new CombinedMetric(javaMetric, kotlinMetric));
    }
    return addSharedMetrics(builder)
        .analyzableSource(
            path -> !JavaSourceFiles.findAll(path).isEmpty() || KotlinMetrics.hasSource(path));
  }

  private static MFCQICalculator.Builder builderFor(Path path, int parallelism) {
    boolean java = !JavaSourceFiles.findAll(path).isEmpty();
    boolean kotlin = KotlinMetrics.hasSource(path);
    if (java && kotlin) {
      return mixedBuilder(parallelism);
    }
    return kotlin ? kotlinBuilder(parallelism) : javaBuilder(parallelism);
  }

  private static List<Metric<?>> javaSourceMetrics() {
    return List.of(
        new CyclomaticComplexity(),
        new CognitiveComplexity(),
        new HalsteadVolume(),
        new MaintainabilityIndex(),
        new CodeDuplication(),
        new DocumentationCoverage(),
        new SecurityMetric(),
        CodeSmellDensity.withDefaultDetectors(),
        new RFCMetric(),
        new DITMetric(),
        new MHFMetric(),
        new CouplingBetweenObjects(),
        new LackOfCohesionOfMethods());
  }

  private static MFCQICalculator.Builder addSharedMetrics(MFCQICalculator.Builder builder) {
    return builder.addMetric(new DependencySecurityMetric()).addMetric(new SecretsExposureMetric());
  }

  private static final class CombinedMetric extends Metric<Double> {
    private final Metric<?> javaMetric;
    private final Metric<?> kotlinMetric;

    private CombinedMetric(Metric<?> javaMetric, Metric<?> kotlinMetric) {
      this.javaMetric = javaMetric;
      this.kotlinMetric = kotlinMetric;
    }

    @Override
    public Double extract(Path codebase) {
      return (javaMetric.calculate(codebase).normalizedValue()
              + kotlinMetric.calculate(codebase).normalizedValue())
          / 2.0;
    }

    @Override
    public double normalize(Double value) {
      return value == null ? 0.0 : value;
    }

    @Override
    public double getWeight() {
      return javaMetric.getWeight();
    }

    @Override
    public String getName() {
      return javaMetric.getName();
    }
  }
}
