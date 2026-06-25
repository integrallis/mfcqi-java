package com.integrallis.mfcqi.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class LLMExceptionTest {

  @Test
  void messageOnlyConstructor() {
    LLMException ex = new LLMException("boom");
    assertThat(ex).hasMessage("boom");
    assertThat(ex.getCause()).isNull();
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }

  @Test
  void messageAndCauseConstructor() {
    Throwable cause = new IllegalStateException("root");
    LLMException ex = new LLMException("wrapped", cause);
    assertThat(ex).hasMessage("wrapped");
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
