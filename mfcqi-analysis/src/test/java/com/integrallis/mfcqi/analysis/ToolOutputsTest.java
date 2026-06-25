package com.integrallis.mfcqi.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ToolOutputsTest {

  @Test
  void banditSummary_countsBySeverity_andSortsTopIssuesDescending() {
    List<ToolOutputs.SecurityIssue> issues =
        Arrays.asList(
            issue("B1", "LOW"),
            issue("B2", "CRITICAL"),
            issue("B3", "MEDIUM"),
            issue("B4", "HIGH"),
            issue("B5", "low"), // case-insensitive default branch
            issue("B6", "WEIRD")); // unknown -> counted as low

    ToolOutputs.BanditSummary summary = new ToolOutputs.BanditSummary(issues);

    assertThat(summary.getCriticalCount()).isEqualTo(1);
    assertThat(summary.getHighCount()).isEqualTo(1);
    assertThat(summary.getMediumCount()).isEqualTo(1);
    assertThat(summary.getLowCount()).isEqualTo(3);
    assertThat(summary.getSummary()).isEqualTo("Found 6 security issues");
    // Highest severity first.
    assertThat(summary.getTopIssues().get(0).getSeverity()).isEqualTo("CRITICAL");
    assertThat(summary.getTopIssues().get(1).getSeverity()).isEqualTo("HIGH");
    assertThat(summary.getTopIssues().get(2).getSeverity()).isEqualTo("MEDIUM");
  }

  @Test
  void banditSummary_capsTopIssuesAtTen() {
    List<ToolOutputs.SecurityIssue> issues = new ArrayList<>();
    for (int i = 0; i < 15; i++) {
      issues.add(issue("B" + i, "HIGH"));
    }
    ToolOutputs.BanditSummary summary = new ToolOutputs.BanditSummary(issues);
    assertThat(summary.getTopIssues()).hasSize(10);
    assertThat(summary.getHighCount()).isEqualTo(15);
  }

  @Test
  void securityIssue_exposesAllFields() {
    ToolOutputs.SecurityIssue i =
        new ToolOutputs.SecurityIssue("B303", "weak_hash", "Crypto.java", 42, "MEDIUM", "MD5 used");
    assertThat(i.getTestId()).isEqualTo("B303");
    assertThat(i.getTestName()).isEqualTo("weak_hash");
    assertThat(i.getFile()).isEqualTo("Crypto.java");
    assertThat(i.getLineNumber()).isEqualTo(42);
    assertThat(i.getSeverity()).isEqualTo("MEDIUM");
    assertThat(i.getMessage()).isEqualTo("MD5 used");
  }

  @Test
  void complexitySummary_sortsByComplexityDescending() {
    ToolOutputs.ComplexitySummary summary =
        new ToolOutputs.ComplexitySummary(
            Arrays.asList(
                new ToolOutputs.ComplexityHotspot("a", "A.java", 1, 5),
                new ToolOutputs.ComplexityHotspot("b", "B.java", 2, 20),
                new ToolOutputs.ComplexityHotspot("c", "C.java", 3, 12)));
    List<ToolOutputs.ComplexityHotspot> fns = summary.getComplexFunctions();
    assertThat(fns.get(0).getComplexity()).isEqualTo(20);
    assertThat(fns.get(1).getComplexity()).isEqualTo(12);
    assertThat(fns.get(2).getComplexity()).isEqualTo(5);
  }

  @Test
  void complexityHotspot_exposesAllFields() {
    ToolOutputs.ComplexityHotspot h =
        new ToolOutputs.ComplexityHotspot("process", "Service.java", 18, 21);
    assertThat(h.getFunctionName()).isEqualTo("process");
    assertThat(h.getFile()).isEqualTo("Service.java");
    assertThat(h.getLineNumber()).isEqualTo(18);
    assertThat(h.getComplexity()).isEqualTo(21);
  }

  @Test
  void builder_buildsFullToolOutputs() {
    ToolOutputs outputs =
        ToolOutputs.builder()
            .bandit(Arrays.asList(issue("B1", "HIGH")))
            .complexity(Arrays.asList(new ToolOutputs.ComplexityHotspot("f", "F.java", 1, 9)))
            .totalFiles(12)
            .totalLines(3400)
            .cyclomaticComplexityRaw(7.5)
            .halsteadVolumeRaw(123.4)
            .build();

    assertThat(outputs.getBandit().getHighCount()).isEqualTo(1);
    assertThat(outputs.getComplexity().getComplexFunctions()).hasSize(1);
    assertThat(outputs.getTotalFiles()).isEqualTo(12);
    assertThat(outputs.getTotalLines()).isEqualTo(3400);
    assertThat(outputs.getCyclomaticComplexityRaw()).isEqualTo(7.5);
    assertThat(outputs.getHalsteadVolumeRaw()).isEqualTo(123.4);
  }

  @Test
  void empty_hasNullSummariesAndZeroCounts() {
    ToolOutputs outputs = ToolOutputs.empty();
    assertThat(outputs.getBandit()).isNull();
    assertThat(outputs.getComplexity()).isNull();
    assertThat(outputs.getTotalFiles()).isZero();
    assertThat(outputs.getTotalLines()).isZero();
    assertThat(outputs.getCyclomaticComplexityRaw()).isZero();
    assertThat(outputs.getHalsteadVolumeRaw()).isZero();
  }

  private static ToolOutputs.SecurityIssue issue(String id, String severity) {
    return new ToolOutputs.SecurityIssue(id, "name_" + id, "File.java", 1, severity, "msg");
  }
}
