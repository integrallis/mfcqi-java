package com.integrallis.mfcqi.badge;

/** shields.io badge style. Mirrors the Python {@code --style} choices. */
public enum BadgeStyle {
  FLAT("flat"),
  FLAT_SQUARE("flat-square"),
  PLASTIC("plastic"),
  FOR_THE_BADGE("for-the-badge");

  private final String value;

  BadgeStyle(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
