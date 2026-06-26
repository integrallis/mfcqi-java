package com.integrallis.mfcqi.cli;

import com.integrallis.mfcqi.core.Version;
import picocli.CommandLine;

/** Supplies the {@code --version} output from the single-sourced build version. */
public final class MfcqiVersionProvider implements CommandLine.IVersionProvider {

  /**
   * Returns the version banner shown by {@code --version}.
   *
   * @return a single-element array, {@code "mfcqi-java <version>"}
   */
  @Override
  public String[] getVersion() {
    return new String[] {"mfcqi-java " + Version.current()};
  }
}
