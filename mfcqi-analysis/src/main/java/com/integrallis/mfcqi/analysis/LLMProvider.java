package com.integrallis.mfcqi.analysis;

/**
 * Strategy interface for LLM completions. Java analog of LiteLLM's provider abstraction in the
 * Python source — implementations exist for Anthropic Messages API, OpenAI Chat Completions, and
 * Ollama's local HTTP API. Implementations may perform network I/O.
 */
public interface LLMProvider {

  /**
   * Provider name used in routing (e.g., {@code "anthropic"}, {@code "openai"}, {@code "ollama"}).
   */
  String name();

  /** True if this provider can serve completions for {@code modelName}. */
  boolean handles(String modelName);

  /** Send {@code prompt} and return the text completion. Throws {@link LLMException} on failure. */
  String complete(String prompt, AnalysisConfig config);
}
