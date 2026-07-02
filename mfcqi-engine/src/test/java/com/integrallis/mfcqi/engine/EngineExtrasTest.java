package com.integrallis.mfcqi.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineExtrasTest {

  @Test
  void metricJson_serializesAStableObject() {
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.75);
    metrics.put("Cyclomatic Complexity", 1.0);
    String json = MetricJson.of(metrics);
    assertThat(json)
        .startsWith("{\n")
        .contains("\"mfcqi_score\": 0.750000")
        .contains("\"Cyclomatic Complexity\": 1.000000")
        .endsWith("}\n");
  }

  @Test
  void calculatorFor_autoSelectsJavaKotlinAndMixed(@TempDir Path dir) throws Exception {
    Path javaOnly = dir.resolve("javaOnly");
    Files.createDirectories(javaOnly);
    Files.writeString(
        javaOnly.resolve("J.java"),
        "package p; public class J { public int a(int x) { return x > 0 ? 1 : 2; } }");

    Path kotlinOnly = dir.resolve("kotlinOnly");
    Files.createDirectories(kotlinOnly);
    Files.writeString(
        kotlinOnly.resolve("K.kt"), "package p\nclass K { fun a(x: Int) = if (x > 0) 1 else 2 }");

    Path mixed = dir.resolve("mixed");
    Files.createDirectories(mixed);
    Files.writeString(
        mixed.resolve("J.java"), "package p; public class J { public int f() { return 1; } }");
    Files.writeString(mixed.resolve("K.kt"), "package p\nfun f() = 1");

    assertThat(MFCQIDefaults.calculatorFor(javaOnly, 2).calculate(javaOnly)).isBetween(0.0, 1.0);
    assertThat(MFCQIDefaults.calculatorFor(kotlinOnly).calculate(kotlinOnly)).isBetween(0.0, 1.0);
    assertThat(MFCQIDefaults.calculatorFor(mixed, 2).calculate(mixed)).isBetween(0.0, 1.0);
  }
}
