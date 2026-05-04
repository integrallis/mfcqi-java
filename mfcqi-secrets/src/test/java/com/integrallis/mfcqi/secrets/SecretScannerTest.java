package com.integrallis.mfcqi.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class SecretScannerTest {

  private final SecretScanner scanner = new SecretScanner();

  @Test
  void shannonEntropy_zeroForUniformString() {
    // All same character -> entropy 0.
    assertThat(SecretScanner.shannonEntropy("aaaaaaaa", "abcdefghij")).isCloseTo(0.0, within(1e-9));
  }

  @Test
  void shannonEntropy_higherForRandomLookingString() {
    double low = SecretScanner.shannonEntropy("aaaaaa", "abcdefghij");
    double high =
        SecretScanner.shannonEntropy(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=");
    assertThat(high).isGreaterThan(low);
  }

  @Test
  void scanFile_findsAwsAccessKey(@TempDir Path tmp) throws Exception {
    Path file = Files.writeString(tmp.resolve("a.java"), "String key = \"AKIAIOSFODNN7EXAMPLE\";");
    List<SecretFinding> findings = scanner.scanFile(file);
    assertThat(findings).extracting(SecretFinding::detectorName).contains("AWSKeyDetector");
  }

  @Test
  void scanFile_findsPrivateKeyHeader(@TempDir Path tmp) throws Exception {
    Path file = Files.writeString(tmp.resolve("a.java"), "-----BEGIN RSA PRIVATE KEY-----\n...");
    List<SecretFinding> findings = scanner.scanFile(file);
    assertThat(findings).extracting(SecretFinding::detectorName).contains("PrivateKeyDetector");
  }

  @Test
  void scanFile_dedupesIdenticalFindings(@TempDir Path tmp) throws Exception {
    // The same line might trigger both a catalog pattern AND an entropy detector.
    Path file = Files.writeString(tmp.resolve("a.java"), "String key = \"AKIAIOSFODNN7EXAMPLE\";");
    List<SecretFinding> findings = scanner.scanFile(file);
    // After dedupe by (file, line, hash), at most one finding per literal value remains.
    long awsCount =
        findings.stream().filter(f -> f.detectorName().equals("AWSKeyDetector")).count();
    assertThat(awsCount).isEqualTo(1);
  }

  @Test
  void scanFile_findsKeywordAssignmentSecret(@TempDir Path tmp) throws Exception {
    Path file = Files.writeString(tmp.resolve("a.yaml"), "api_key: \"verysecretvalue123\"");
    List<SecretFinding> findings = scanner.scanFile(file);
    assertThat(findings).extracting(SecretFinding::detectorName).contains("KeywordDetector");
  }

  @Test
  void scanFile_emptyFileHasNoFindings(@TempDir Path tmp) throws Exception {
    Path file = Files.writeString(tmp.resolve("a.java"), "");
    assertThat(scanner.scanFile(file)).isEmpty();
  }

  @Test
  void scanDirectory_aggregatesAcrossFiles(@TempDir Path tmp) throws Exception {
    Files.createDirectories(tmp.resolve("a"));
    Files.writeString(tmp.resolve("a/x.java"), "String k = \"AKIAIOSFODNN7EXAMPLE\";");
    Files.writeString(tmp.resolve("a/y.java"), "// no secret here");
    assertThat(scanner.scanDirectory(tmp)).hasSizeGreaterThanOrEqualTo(1);
  }
}
