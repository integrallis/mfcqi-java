package com.integrallis.mfcqi.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Exposes the build version, single-sourced from {@code gradle.properties}. The Gradle build
 * generates a {@code mfcqi-version.properties} resource from {@code project.version}; this class
 * reads it from the classpath so the version never has to be duplicated in source.
 */
public final class Version {

  private static final String VERSION = load();

  private Version() {}

  /**
   * Returns the project version (for example {@code 0.1.0}), or {@code "unknown"} when the
   * generated version resource is not on the classpath.
   *
   * @return the build version string
   */
  public static String current() {
    return VERSION;
  }

  private static String load() {
    try (InputStream in = Version.class.getResourceAsStream("/mfcqi-version.properties")) {
      if (in == null) {
        return "unknown";
      }
      Properties props = new Properties();
      props.load(in);
      return props.getProperty("version", "unknown");
    } catch (IOException e) {
      return "unknown";
    }
  }
}
