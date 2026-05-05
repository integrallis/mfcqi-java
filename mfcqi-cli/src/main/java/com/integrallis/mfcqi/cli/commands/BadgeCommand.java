package com.integrallis.mfcqi.cli.commands;

import com.integrallis.mfcqi.badge.BadgeGenerator;
import com.integrallis.mfcqi.badge.BadgeStyle;
import com.integrallis.mfcqi.cli.MFCQIDefaults;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code mfcqi badge} — generates a shields.io badge artifact (URL, endpoint JSON, or markdown
 * snippet) for a codebase's MFCQI score. Mirrors {@code mfcqi/cli/commands/badge.py}.
 */
@Command(
    name = "badge",
    mixinStandardHelpOptions = true,
    description = "Generate an MFCQI badge for the given codebase.")
public final class BadgeCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Path to the codebase directory.", arity = "0..1")
  Path path = Path.of(".");

  @Option(
      names = {"--format", "-f"},
      description = "Output format: url (default), json, markdown.")
  String format = "url";

  @Option(
      names = {"--style"},
      description = "Badge style: flat, flat-square, plastic, for-the-badge.")
  String styleName = "flat";

  @Option(
      names = {"--output", "-o"},
      description = "Write the badge artifact to FILE instead of stdout.")
  Path output;

  @Override
  public Integer call() {
    double score = MFCQIDefaults.calculator().calculate(path);
    BadgeStyle style = parseStyle(styleName);
    String artifact;
    switch (format.toLowerCase(Locale.ROOT)) {
      case "json":
        artifact = BadgeGenerator.endpointJson(score);
        break;
      case "markdown":
        artifact = BadgeGenerator.markdown(score, style);
        break;
      case "url":
      default:
        artifact = BadgeGenerator.url(score, style);
        break;
    }
    if (output == null) {
      System.out.println(artifact);
    } else {
      try {
        Files.write(output, artifact.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        System.err.println("Failed to write " + output + ": " + e.getMessage());
        return 2;
      }
    }
    return 0;
  }

  static BadgeStyle parseStyle(String value) {
    switch (value.toLowerCase(Locale.ROOT)) {
      case "flat-square":
        return BadgeStyle.FLAT_SQUARE;
      case "plastic":
        return BadgeStyle.PLASTIC;
      case "for-the-badge":
        return BadgeStyle.FOR_THE_BADGE;
      case "flat":
      default:
        return BadgeStyle.FLAT;
    }
  }
}
