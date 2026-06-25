package com.integrallis.mfcqi.secrets;

import java.nio.file.Path;
import java.util.Objects;

/** A single secret detected in a source file: which detector fired, where, and why. */
public final class SecretFinding {

  private final Path file;
  private final int lineNumber;
  private final String detectorName;
  private final String hashedValue;

  public SecretFinding(Path file, int lineNumber, String detectorName, String hashedValue) {
    this.file = Objects.requireNonNull(file, "file");
    this.lineNumber = lineNumber;
    this.detectorName = Objects.requireNonNull(detectorName, "detectorName");
    this.hashedValue = Objects.requireNonNull(hashedValue, "hashedValue");
  }

  /**
   * Returns the file in which the secret was found.
   *
   * @return the source file
   */
  public Path file() {
    return file;
  }

  /**
   * Returns the 1-based line number where the secret was found.
   *
   * @return the line number
   */
  public int lineNumber() {
    return lineNumber;
  }

  /**
   * Returns the name of the detector (regex plugin or entropy filter) that fired.
   *
   * @return the detector name
   */
  public String detectorName() {
    return detectorName;
  }

  /**
   * Returns a SHA-1 hash of the matched secret value, used for de-duplication without storing the
   * plaintext secret.
   *
   * @return the hashed secret value
   */
  public String hashedValue() {
    return hashedValue;
  }

  @Override
  public String toString() {
    return detectorName + " at " + file.getFileName() + ":" + lineNumber;
  }
}
