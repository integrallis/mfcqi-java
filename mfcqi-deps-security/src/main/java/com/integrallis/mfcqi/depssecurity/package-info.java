/**
 * Dependency-vulnerability metric. Scans Maven/Gradle dependency descriptors with OWASP
 * Dependency-Check core, sums CVE severities (weighted CRITICAL &gt; HIGH &gt; MEDIUM &gt; LOW),
 * normalizes per dependency count, and exponentially decays into {@code [0,1]}.
 *
 * <p>Uses the public NVD data feed; an NVD API key is supported but not required.
 */
package com.integrallis.mfcqi.depssecurity;
