package com.integrallis.mfcqi.depssecurity;

import java.util.Objects;

/**
 * A declared external dependency: ecosystem (e.g., "Maven"), package coordinates ("group:artifact"
 * or single name), and a literal version string. Used as input to a {@link VulnerabilityScanner}.
 */
public final class Dependency {

  private final String ecosystem;
  private final String packageName;
  private final String version;

  /**
   * Creates a dependency. All arguments are required.
   *
   * @param ecosystem the package ecosystem (e.g., {@code "Maven"})
   * @param packageName the package coordinates ({@code "group:artifact"} or a single name)
   * @param version the literal version string
   */
  public Dependency(String ecosystem, String packageName, String version) {
    this.ecosystem = Objects.requireNonNull(ecosystem, "ecosystem");
    this.packageName = Objects.requireNonNull(packageName, "packageName");
    this.version = Objects.requireNonNull(version, "version");
  }

  /**
   * The package ecosystem (e.g., {@code "Maven"}).
   *
   * @return the ecosystem
   */
  public String ecosystem() {
    return ecosystem;
  }

  /**
   * The package coordinates ({@code "group:artifact"} or a single name).
   *
   * @return the package name
   */
  public String packageName() {
    return packageName;
  }

  /**
   * The literal version string.
   *
   * @return the version
   */
  public String version() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Dependency)) {
      return false;
    }
    Dependency d = (Dependency) o;
    return ecosystem.equals(d.ecosystem)
        && packageName.equals(d.packageName)
        && version.equals(d.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ecosystem, packageName, version);
  }

  @Override
  public String toString() {
    return ecosystem + ":" + packageName + ":" + version;
  }
}
