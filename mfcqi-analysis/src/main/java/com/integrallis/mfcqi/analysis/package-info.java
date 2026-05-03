/**
 * Optional LLM analysis engine. Receives the pre-calculated MFCQI breakdown plus raw tool outputs
 * (security findings, complex functions, etc.) and renders a Pebble template into a prompt sent to
 * Anthropic, OpenAI, or a local Ollama endpoint.
 *
 * <p>The LLM call is always optional — the CLI's {@code --skip-llm} flag bypasses this module
 * entirely. User-supplied API keys are required at runtime for hosted providers; Ollama needs only
 * a local server.
 */
package com.integrallis.mfcqi.analysis;
