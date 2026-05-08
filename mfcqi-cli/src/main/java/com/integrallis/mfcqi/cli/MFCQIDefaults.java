package com.integrallis.mfcqi.cli;

import com.integrallis.mfcqi.core.MFCQICalculator;
import com.integrallis.mfcqi.depssecurity.DependencySecurityMetric;
import com.integrallis.mfcqi.duplication.CodeDuplication;
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
import com.integrallis.mfcqi.smells.CodeSmellDensity;

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
    return MFCQICalculator.builder()
        .addMetric(new CyclomaticComplexity())
        .addMetric(new CognitiveComplexity())
        .addMetric(new HalsteadVolume())
        .addMetric(new MaintainabilityIndex())
        .addMetric(new CodeDuplication())
        .addMetric(new DocumentationCoverage())
        .addMetric(new SecurityMetric())
        .addMetric(new DependencySecurityMetric())
        .addMetric(new SecretsExposureMetric())
        .addMetric(CodeSmellDensity.withDefaultDetectors())
        .addMetric(new RFCMetric())
        .addMetric(new DITMetric())
        .addMetric(new MHFMetric())
        .addMetric(new CouplingBetweenObjects())
        .addMetric(new LackOfCohesionOfMethods())
        .build();
  }
}
