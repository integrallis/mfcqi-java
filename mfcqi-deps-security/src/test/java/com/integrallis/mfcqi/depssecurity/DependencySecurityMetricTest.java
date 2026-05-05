package com.integrallis.mfcqi.depssecurity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class DependencySecurityMetricTest {

  @Test
  void weightAndNameMatchPythonReference() {
    DependencySecurityMetric metric = new DependencySecurityMetric();
    assertThat(metric.getWeight()).isEqualTo(0.75);
    assertThat(metric.getName()).isEqualTo("Dependency Security");
  }

  @Test
  void normalize_zeroVulnsReturnsOne() {
    DependencySecurityMetric metric = new DependencySecurityMetric();
    assertThat(metric.normalize(0.0)).isEqualTo(1.0);
  }

  @Test
  void normalize_appliesExponentialDecay() {
    DependencySecurityMetric metric = new DependencySecurityMetric();
    // Python formula: exp(-value / 5.0). At value=10 => exp(-2) ≈ 0.1353.
    assertThat(metric.normalize(10.0)).isCloseTo(Math.exp(-2.0), within(1e-9));
  }

  @Test
  void extract_emptyCodebaseReturnsZero(@TempDir Path tmp) {
    // Use the offline scanner explicitly to avoid network during the test.
    assertThat(new DependencySecurityMetric(new OfflineNoOpScanner()).extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_offlineScannerProducesZeroVulnsForRealManifest(@TempDir Path tmp) throws Exception {
    Files.writeString(
        tmp.resolve("pom.xml"),
        "<project><dependencies>"
            + "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>1.0</version></dependency>"
            + "</dependencies></project>");
    // OfflineNoOpScanner is the documented opt-out for deterministic offline runs.
    DependencySecurityMetric metric = new DependencySecurityMetric(new OfflineNoOpScanner());
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void defaultConstructor_wiresOsvScanner() {
    // The Python source's pip-audit equivalent for Java is OSV.dev (no API key, public). The
    // default ctor must wire that, not the offline no-op. Stronger than the previous "non-null"
    // assertion: pin the scanner *type* so a regression back to OfflineNoOpScanner would be
    // caught immediately.
    DependencySecurityMetric metric = new DependencySecurityMetric();
    assertThat(metric.scanner()).isInstanceOf(OsvVulnerabilityScanner.class);
    assertThat(metric.scanner()).isNotInstanceOf(OfflineNoOpScanner.class);
  }

  @Test
  void explicitConstructor_keepsTheInjectedScanner() {
    // Symmetric guarantee: opt-out path keeps OfflineNoOpScanner so deterministic tests don't
    // accidentally hit the network.
    DependencySecurityMetric metric = new DependencySecurityMetric(new OfflineNoOpScanner());
    assertThat(metric.scanner()).isInstanceOf(OfflineNoOpScanner.class);
  }

  @Test
  void extract_appliesUniformWeightPerVulnFromInjectedScanner(@TempDir Path tmp) throws Exception {
    Files.writeString(
        tmp.resolve("pom.xml"),
        "<project><dependencies>"
            + "<dependency><groupId>g</groupId><artifactId>a</artifactId><version>1.0</version></dependency>"
            + "<dependency><groupId>g2</groupId><artifactId>a2</artifactId><version>2.0</version></dependency>"
            + "</dependencies></project>");

    Map<String, List<Vulnerability>> stub = new HashMap<>();
    stub.put(
        "g:a",
        Arrays.asList(
            new Vulnerability("CVE-2024-0001", "test"),
            new Vulnerability("CVE-2024-0002", "test")));
    stub.put("g2:a2", Collections.singletonList(new Vulnerability("CVE-2024-0003", "test")));

    VulnerabilityScanner scanner =
        dep -> stub.getOrDefault(dep.packageName(), Collections.emptyList());

    DependencySecurityMetric metric = new DependencySecurityMetric(scanner);
    // 3 vulns total * weight 2.0 = 6.0
    assertThat(metric.extract(tmp)).isCloseTo(6.0, within(1e-9));
  }
}
