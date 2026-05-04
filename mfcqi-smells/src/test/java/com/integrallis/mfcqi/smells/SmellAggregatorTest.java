package com.integrallis.mfcqi.smells;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class SmellAggregatorTest {

  @Test
  void severityDefaultWeightsMatchPythonReference() {
    assertThat(SmellSeverity.HIGH.defaultWeight()).isEqualTo(3.0);
    assertThat(SmellSeverity.MEDIUM.defaultWeight()).isEqualTo(2.0);
    assertThat(SmellSeverity.LOW.defaultWeight()).isEqualTo(1.0);
  }

  @Test
  void smellAppliesDefaultSeverityWeight() {
    Smell s = smell("S1", SmellSeverity.HIGH, "a.java:1");
    assertThat(s.severityWeight()).isEqualTo(3.0);
  }

  @Test
  void deduplicate_keepsHighestSeverityForSameIdAndLocation() {
    Smell low = smell("LONG_METHOD", SmellSeverity.LOW, "a.java:10");
    Smell high = smell("LONG_METHOD", SmellSeverity.HIGH, "a.java:10");
    Smell other = smell("GOD_CLASS", SmellSeverity.MEDIUM, "a.java:10");
    List<Smell> deduped = SmellAggregator.deduplicate(Arrays.asList(low, high, other));
    assertThat(deduped)
        .extracting(Smell::severity)
        .containsExactlyInAnyOrder(SmellSeverity.HIGH, SmellSeverity.MEDIUM);
  }

  @Test
  void detectAll_concatenatesDetectorOutputAndDedupes(@TempDir Path tmp) {
    SmellDetector a = stub("a", smell("LONG_METHOD", SmellSeverity.LOW, "x.java:1"));
    SmellDetector b = stub("b", smell("LONG_METHOD", SmellSeverity.HIGH, "x.java:1"));
    SmellAggregator agg = new SmellAggregator(Arrays.asList(a, b));
    assertThat(agg.detectAll(tmp))
        .hasSize(1)
        .first()
        .satisfies(s -> assertThat(s.severity()).isEqualTo(SmellSeverity.HIGH));
  }

  @Test
  void countByCategory_sumsCounts(@TempDir Path tmp) {
    SmellDetector a =
        stub(
            "a",
            smell("S1", SmellSeverity.HIGH, "a.java:1", SmellCategory.IMPLEMENTATION),
            smell("S2", SmellSeverity.MEDIUM, "a.java:2", SmellCategory.IMPLEMENTATION),
            smell("S3", SmellSeverity.HIGH, "a.java:3", SmellCategory.TEST));
    SmellAggregator agg = new SmellAggregator(Collections.singletonList(a));
    assertThat(agg.countByCategory(tmp))
        .containsEntry(SmellCategory.IMPLEMENTATION, 2)
        .containsEntry(SmellCategory.TEST, 1);
  }

  @Test
  void weightedCountByCategory_sumsSeverityWeights(@TempDir Path tmp) {
    SmellDetector a =
        stub(
            "a",
            smell("S1", SmellSeverity.HIGH, "a.java:1", SmellCategory.IMPLEMENTATION),
            smell("S2", SmellSeverity.MEDIUM, "a.java:2", SmellCategory.IMPLEMENTATION),
            smell("S3", SmellSeverity.LOW, "a.java:3", SmellCategory.TEST));
    SmellAggregator agg = new SmellAggregator(Collections.singletonList(a));
    // IMPLEMENTATION: 3.0 (HIGH) + 2.0 (MEDIUM) = 5.0; TEST: 1.0 (LOW)
    assertThat(agg.weightedCountByCategory(tmp))
        .containsEntry(SmellCategory.IMPLEMENTATION, 5.0)
        .containsEntry(SmellCategory.TEST, 1.0);
  }

  private static Smell smell(String id, SmellSeverity sev, String location) {
    return smell(id, sev, location, SmellCategory.IMPLEMENTATION);
  }

  private static Smell smell(String id, SmellSeverity sev, String location, SmellCategory cat) {
    return new Smell(id, id, cat, sev, location, "test-tool", "desc");
  }

  private static SmellDetector stub(String name, Smell... smells) {
    return new SmellDetector() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public List<Smell> detect(Path codebase) {
        return Arrays.asList(smells);
      }
    };
  }
}
