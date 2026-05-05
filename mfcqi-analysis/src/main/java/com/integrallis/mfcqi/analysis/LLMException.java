package com.integrallis.mfcqi.analysis;

/** Thrown when an LLM provider call fails. */
public final class LLMException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LLMException(String message) {
    super(message);
  }

  public LLMException(String message, Throwable cause) {
    super(message, cause);
  }
}
