package com.integrallis.mfcqi.depssecurity;

import java.util.Collections;
import java.util.List;

/**
 * Default {@link VulnerabilityScanner} that returns no vulnerabilities for any dependency. Lets the
 * metric run deterministically without network access; callers wire in a real scanner (e.g., {@code
 * OsvVulnerabilityScanner} or an OWASP Dependency-Check binding) when they want actual CVE data.
 */
public final class OfflineNoOpScanner implements VulnerabilityScanner {

  @Override
  public List<Vulnerability> scan(Dependency dependency) {
    return Collections.emptyList();
  }
}
