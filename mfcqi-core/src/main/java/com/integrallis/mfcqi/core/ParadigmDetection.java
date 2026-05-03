package com.integrallis.mfcqi.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome of paradigm detection. Carries the headline {@link Paradigm} plus the underlying score
 * (an "OO score" in {@code [0,1]}) and any per-signal metrics the detector chose to expose for
 * diagnostics.
 */
public final class ParadigmDetection {

  private final Paradigm paradigm;
  private final double ooScore;
  private final Map<String, Double> signals;

  public ParadigmDetection(Paradigm paradigm, double ooScore, Map<String, Double> signals) {
    this.paradigm = Objects.requireNonNull(paradigm, "paradigm");
    this.ooScore = ooScore;
    this.signals = Collections.unmodifiableMap(new LinkedHashMap<>(signals));
  }

  public Paradigm paradigm() {
    return paradigm;
  }

  public double ooScore() {
    return ooScore;
  }

  public Map<String, Double> signals() {
    return signals;
  }

  @Override
  public String toString() {
    return "ParadigmDetection{paradigm=" + paradigm + ", ooScore=" + ooScore + '}';
  }
}
