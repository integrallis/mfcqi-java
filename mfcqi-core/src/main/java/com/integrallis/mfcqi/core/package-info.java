/**
 * Framework for the Multi-Factor Code Quality Index (MFCQI).
 *
 * <p>Defines the {@link com.integrallis.mfcqi.core.Metric} contract (Template Method pattern), the
 * {@link com.integrallis.mfcqi.core.MFCQICalculator} that aggregates metric results via weighted
 * geometric mean, and the {@link com.integrallis.mfcqi.core.JavaSourceFiles} utility for walking a
 * codebase.
 *
 * <p>Concrete metric implementations live in sibling modules (e.g. {@code mfcqi-metrics}) so that
 * this module stays free of analyzer dependencies (JavaParser, PMD, SpotBugs).
 */
package com.integrallis.mfcqi.core;
