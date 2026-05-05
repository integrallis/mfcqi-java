package com.integrallis.mfcqi.cli;

import com.integrallis.mfcqi.cli.commands.AnalyzeCommand;
import com.integrallis.mfcqi.cli.commands.BadgeCommand;
import com.integrallis.mfcqi.cli.commands.ConfigCommand;
import com.integrallis.mfcqi.cli.commands.QualityGateCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Top-level entry point for the {@code mfcqi} CLI. Mirrors the Python {@code mfcqi.cli.main} Click
 * group and its subcommands ({@code analyze}, {@code badge}, {@code config}, {@code quality-gate}).
 */
@Command(
    name = "mfcqi",
    mixinStandardHelpOptions = true,
    version = "mfcqi-java 0.1.0-SNAPSHOT",
    description = "Multi-Factor Code Quality Index for Java codebases.",
    subcommands = {
      AnalyzeCommand.class,
      BadgeCommand.class,
      ConfigCommand.class,
      QualityGateCommand.class
    })
public final class Main implements Runnable {

  @Override
  public void run() {
    // No subcommand — Picocli will print usage automatically when --help is passed; otherwise
    // print the same hint Python's empty group does.
    System.out.println("mfcqi-java — Multi-Factor Code Quality Index");
    System.out.println("Run with --help to see available subcommands.");
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }
}
