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
    assertThat(new DependencySecurityMetric().extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_offlineDefaultProducesZeroVulnsForRealManifest(@TempDir Path tmp) throws Exception {
    Files.writeString(
        tmp.resolve("pom.xml"),
        "<project><dependencies>"
            + "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>1.0</version></dependency>"
            + "</dependencies></project>");
    // Default scanner is offline / no-op -> zero vulns -> extract 0.0 -> normalize 1.0.
    DependencySecurityMetric metric = new DependencySecurityMetric();
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
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
