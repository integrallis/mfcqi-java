package com.integrallis.mfcqi.cli.commands;

import com.integrallis.mfcqi.analysis.AnalysisConfig;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * {@code mfcqi config} — displays the LLM provider configuration assembled from environment
 * variables. Mirrors the {@code status} subcommand from {@code mfcqi/cli/commands/config.py}; the
 * Python {@code set-key} flow uses the {@code keyring} library, which has no portable Java
 * equivalent — users set {@code ANTHROPIC_API_KEY}/{@code OPENAI_API_KEY} directly.
 */
@Command(
    name = "config",
    mixinStandardHelpOptions = true,
    description = "Show the active LLM analysis configuration.")
public final class ConfigCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    AnalysisConfig cfg = AnalysisConfig.fromEnvironment();
    System.out.println("model:               " + cfg.model());
    System.out.println("temperature:         " + cfg.temperature());
    System.out.println("max_tokens:          " + cfg.maxTokens());
    System.out.println("timeout_seconds:     " + cfg.timeoutSeconds());
    System.out.println(
        "anthropic_api_key:   " + (cfg.anthropicApiKey().isPresent() ? "<set>" : "<unset>"));
    System.out.println(
        "openai_api_key:      " + (cfg.openaiApiKey().isPresent() ? "<set>" : "<unset>"));
    System.out.println();
    System.out.println("Supported models: " + cfg.supportedModels());
    return 0;
  }
}
