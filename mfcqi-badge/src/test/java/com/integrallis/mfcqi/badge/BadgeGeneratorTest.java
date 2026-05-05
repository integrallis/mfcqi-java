package com.integrallis.mfcqi.badge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class BadgeGeneratorTest {

  @Test
  void rating_thresholdsMatchPythonReference() {
    assertThat(BadgeRating.forScore(0.85)).isEqualTo(BadgeRating.EXCELLENT);
    assertThat(BadgeRating.forScore(0.80)).isEqualTo(BadgeRating.EXCELLENT);
    assertThat(BadgeRating.forScore(0.79)).isEqualTo(BadgeRating.GOOD);
    assertThat(BadgeRating.forScore(0.60)).isEqualTo(BadgeRating.GOOD);
    assertThat(BadgeRating.forScore(0.59)).isEqualTo(BadgeRating.FAIR);
    assertThat(BadgeRating.forScore(0.40)).isEqualTo(BadgeRating.FAIR);
    assertThat(BadgeRating.forScore(0.39)).isEqualTo(BadgeRating.POOR);
    assertThat(BadgeRating.forScore(0.0)).isEqualTo(BadgeRating.POOR);
  }

  @Test
  void formatScore_usesTwoDecimals() {
    assertThat(BadgeGenerator.formatScore(0.854)).isEqualTo("0.85");
    assertThat(BadgeGenerator.formatScore(1.0)).isEqualTo("1.00");
    assertThat(BadgeGenerator.formatScore(0.0)).isEqualTo("0.00");
  }

  @Test
  void url_buildsShieldsIoUrlWithStyle() {
    String url = BadgeGenerator.url(0.85, BadgeStyle.FLAT);
    assertThat(url).isEqualTo("https://img.shields.io/badge/MFCQI-0.85-brightgreen.svg?style=flat");
  }

  @Test
  void url_includesYellowForFairScore() {
    assertThat(BadgeGenerator.url(0.50, BadgeStyle.FOR_THE_BADGE))
        .isEqualTo("https://img.shields.io/badge/MFCQI-0.50-yellow.svg?style=for-the-badge");
  }

  @Test
  void url_includesRedForPoorScore() {
    assertThat(BadgeGenerator.url(0.10, BadgeStyle.FLAT_SQUARE))
        .isEqualTo("https://img.shields.io/badge/MFCQI-0.10-red.svg?style=flat-square");
  }

  @Test
  void endpointJson_matchesPythonStructure() {
    String json = BadgeGenerator.endpointJson(0.85);
    assertThat(json)
        .contains("\"schemaVersion\": 1")
        .contains("\"label\": \"MFCQI\"")
        .contains("\"message\": \"0.85 (excellent)\"")
        .contains("\"color\": \"brightgreen\"")
        .contains("\"cacheSeconds\": 3600");
  }

  @Test
  void markdown_wrapsUrlInImageSyntax() {
    assertThat(BadgeGenerator.markdown(0.65, BadgeStyle.FLAT))
        .isEqualTo("![MFCQI Score](https://img.shields.io/badge/MFCQI-0.65-green.svg?style=flat)");
  }
}
