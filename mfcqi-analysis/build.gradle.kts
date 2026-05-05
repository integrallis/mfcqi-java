description =
    "Optional LLM analysis engine — generates prioritized recommendations from the metric breakdown via Anthropic, OpenAI, or Ollama"

dependencies {
    "api"(project(":mfcqi-core"))
    // Jackson for JSON request/response — Apache 2.0, no API key.
    "implementation"("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    // Pebble for Jinja2-compatible prompt templates — BSD 3-Clause, no API key.
    "implementation"("io.pebbletemplates:pebble:3.2.2")
}
