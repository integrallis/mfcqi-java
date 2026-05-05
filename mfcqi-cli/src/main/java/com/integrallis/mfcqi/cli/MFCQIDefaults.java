package com.integrallis.mfcqi.cli;

import com.integrallis.mfcqi.core.MFCQICalculator;
import com.integrallis.mfcqi.core.Paradigm;
import com.integrallis.mfcqi.depssecurity.DependencySecurityMetric;
import com.integrallis.mfcqi.duplication.CodeDuplication;
import com.integrallis.mfcqi.metrics.CognitiveComplexity;
import com.integrallis.mfcqi.metrics.CouplingBetweenObjects;
import com.integrallis.mfcqi.metrics.CyclomaticComplexity;
import com.integrallis.mfcqi.metrics.DITMetric;
import com.integrallis.mfcqi.metrics.DocumentationCoverage;
import com.integrallis.mfcqi.metrics.HalsteadVolume;
import com.integrallis.mfcqi.metrics.JavaParadigmDetector;
import com.integrallis.mfcqi.metrics.LackOfCohesionOfMethods;
import com.integrallis.mfcqi.metrics.MHFMetric;
import com.integrallis.mfcqi.metrics.MaintainabilityIndex;
import com.integrallis.mfcqi.metrics.RFCMetric;
import com.integrallis.mfcqi.secrets.SecretsExposureMetric;
import com.integrallis.mfcqi.security.SecurityMetric;
import com.integrallis.mfcqi.smells.CodeSmellDensity;

/**
 * Factory that wires every Phase 2–6 metric into a default-configured {@link MFCQICalculator},
 * mirroring the registration the Python {@code MFCQICalculator.__init__} performs.
 *
 * <p>Always-on core metrics (10): CyclomaticComplexity, CognitiveComplexity, HalsteadVolume,
 * MaintainabilityIndex, CodeDuplication, DocumentationCoverage, SecurityMetric,
 * DependencySecurityMetric, SecretsExposureMetric, CodeSmellDensity (with its default detectors).
 *
 * <p>Paradigm-conditional OO metrics, registered through the {@link
 * com.integrallis.mfcqi.metrics.JavaParadigmDetector}:
 *
 * <ul>
 *   <li>{@link Paradigm#STRONG_OO} and {@link Paradigm#MIXED_OO}: all five OO metrics — RFC, DIT,
 *       MHF, CBO, LCOM
 *   <li>{@link Paradigm#WEAK_OO}: RFC only
 *   <li>{@link Paradigm#PROCEDURAL}: no OO metrics
 * </ul>
 */
public final class MFCQIDefaults {

  private MFCQIDefaults() {}

  /** Build a {@link MFCQICalculator} with the default metric registry. */
  public static MFCQICalculator calculator() {
    MFCQICalculator.Builder b =
        MFCQICalculator.builder()
            .paradigmDetector(new JavaParadigmDetector())
            // Core metrics — always evaluated.
            .addCoreMetric(new CyclomaticComplexity())
            .addCoreMetric(new CognitiveComplexity())
            .addCoreMetric(new HalsteadVolume())
            .addCoreMetric(new MaintainabilityIndex())
            .addCoreMetric(new CodeDuplication())
            .addCoreMetric(new DocumentationCoverage())
            .addCoreMetric(new SecurityMetric())
            .addCoreMetric(new DependencySecurityMetric())
            .addCoreMetric(new SecretsExposureMetric())
            .addCoreMetric(CodeSmellDensity.withDefaultDetectors());

    // OO metrics — registered per paradigm. Mirrors Python's _add_oo_metrics_for_paradigm map.
    for (Paradigm p : new Paradigm[] {Paradigm.STRONG_OO, Paradigm.MIXED_OO}) {
      b.addParadigmMetric(p, new RFCMetric())
          .addParadigmMetric(p, new DITMetric())
          .addParadigmMetric(p, new MHFMetric())
          .addParadigmMetric(p, new CouplingBetweenObjects())
          .addParadigmMetric(p, new LackOfCohesionOfMethods());
    }
    b.addParadigmMetric(Paradigm.WEAK_OO, new RFCMetric());

    return b.build();
  }
}
