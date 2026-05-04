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

  public Path file() {
    return file;
  }

  public int lineNumber() {
    return lineNumber;
  }

  public String detectorName() {
    return detectorName;
  }

  public String hashedValue() {
    return hashedValue;
  }

  @Override
  public String toString() {
    return detectorName + " at " + file.getFileName() + ":" + lineNumber;
  }
}
