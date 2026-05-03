package com.integrallis.mfcqi.cli;

/**
 * Entry point for the {@code mfcqi} CLI. Subcommands ({@code analyze}, {@code badge}, {@code
 * config}, {@code models}) are wired in subsequent phases of the port using Picocli; for now this
 * is a placeholder that prints a banner so the {@code mfcqi-cli} module has a runnable main.
 */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    System.out.println("mfcqi-java — Multi-Factor Code Quality Index");
    System.out.println("CLI not yet implemented. See README for roadmap.");
  }
}
