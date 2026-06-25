package com.integrallis.mfcqi.analysis;

/** Thrown when an LLM provider call fails. */
public final class LLMException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception with the given detail message.
   *
   * @param message the detail message
   */
  public LLMException(String message) {
    super(message);
  }

  /**
   * Creates an exception with the given detail message and underlying cause.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public LLMException(String message, Throwable cause) {
    super(message, cause);
  }
}
