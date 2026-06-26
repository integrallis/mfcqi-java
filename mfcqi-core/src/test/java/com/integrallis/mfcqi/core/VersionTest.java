package com.integrallis.mfcqi.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class VersionTest {

  @Test
  void current_isResolvedFromTheGeneratedResource() {
    // The Gradle build generates mfcqi-version.properties from project.version, so at test time
    // this must resolve to a real semantic version (not the "unknown" fallback).
    String version = Version.current();
    assertThat(version).isNotBlank().isNotEqualTo("unknown");
    assertThat(version).matches("\\d+\\.\\d+\\.\\d+.*");
  }

  @Test
  void current_isStable() {
    assertThat(Version.current()).isEqualTo(Version.current());
  }
}
