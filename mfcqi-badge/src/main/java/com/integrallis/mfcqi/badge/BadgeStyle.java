package com.integrallis.mfcqi.badge;

/** shields.io badge style. Mirrors the Python {@code --style} choices. */
public enum BadgeStyle {
  /** The {@code flat} shields.io style. */
  FLAT("flat"),
  /** The {@code flat-square} shields.io style. */
  FLAT_SQUARE("flat-square"),
  /** The {@code plastic} shields.io style. */
  PLASTIC("plastic"),
  /** The {@code for-the-badge} shields.io style. */
  FOR_THE_BADGE("for-the-badge");

  private final String value;

  BadgeStyle(String value) {
    this.value = value;
  }

  /**
   * Returns the shields.io style token used in a badge URL's {@code style} query parameter.
   *
   * @return the style value (e.g. {@code "flat-square"})
   */
  public String value() {
    return value;
  }
}
