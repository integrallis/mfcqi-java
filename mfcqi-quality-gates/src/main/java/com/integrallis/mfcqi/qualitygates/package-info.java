/**
 * Quality-gate evaluator. Loads {@code .mfcqi.yaml} (or {@code .mfcqi-gates.yaml}) describing
 * minimum thresholds for the overall MFCQI score and individual metrics, then evaluates an analysis
 * result against those thresholds. CLI integration drives the build's exit code.
 */
package com.integrallis.mfcqi.qualitygates;
