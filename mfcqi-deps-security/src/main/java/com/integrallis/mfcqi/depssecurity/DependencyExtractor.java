package com.integrallis.mfcqi.depssecurity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Walks a codebase for dependency-declaration files and extracts {@link Dependency} entries from
 * each. Mirrors the Python {@code DependencySecurityMetric.extract} file-discovery loop, adapted
 * for Java/Maven and Gradle conventions.
 *
 * <ul>
 *   <li>Maven {@code pom.xml} — proper XML parsing, reads {@code <dependency>}/<groupId/
 *       artifactId/version>
 *   <li>Gradle {@code build.gradle} / {@code build.gradle.kts} — regex-based extraction of literal
 *       {@code group:artifact:version} strings inside {@code implementation}/{@code api} /{@code
 *       testImplementation}/{@code runtimeOnly}/{@code compileOnly} declarations
 * </ul>
 *
 * <p>Dynamic versions, version catalogs ({@code libs.versions.toml}), and Gradle variables are not
 * resolved — this matches the Python source's level of effort (pip-audit also operates on literal
 * entries in {@code requirements.txt}).
 */
public final class DependencyExtractor {

  private static final Pattern GAV =
      Pattern.compile(
          "(?:implementation|api|testImplementation|testRuntimeOnly|runtimeOnly|compileOnly|annotationProcessor)\\s*[\\(]?\\s*[\"']([^\"':]+):([^\"':]+):([^\"':]+)[\"']");

  private DependencyExtractor() {}

  /**
   * Find every dependency declared anywhere under {@code root}. Walks the tree for Maven {@code
   * pom.xml} and Gradle {@code build.gradle(.kts)} files and aggregates their declared
   * dependencies.
   *
   * @param root the directory to search; a non-directory or {@code null} yields an empty list
   * @return the discovered dependencies (possibly with duplicates across files)
   */
  public static List<Dependency> extractAll(Path root) {
    if (root == null || !Files.isDirectory(root)) {
      return Collections.emptyList();
    }
    List<Dependency> deps = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(root)) {
      walk.filter(Files::isRegularFile)
          .forEach(
              p -> {
                String name = p.getFileName().toString();
                if ("pom.xml".equals(name)) {
                  deps.addAll(parseMavenPom(p));
                } else if ("build.gradle".equals(name) || "build.gradle.kts".equals(name)) {
                  deps.addAll(parseGradleBuildScript(p));
                }
              });
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to walk " + root, e);
    }
    return deps;
  }

  /** Parse a Maven {@code pom.xml} for {@code <dependency>} entries. */
  static List<Dependency> parseMavenPom(Path pom) {
    List<Dependency> out = new ArrayList<>();
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      // Mitigate XXE — we only need plain-text traversal.
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(pom.toFile());
      NodeList deps = doc.getElementsByTagName("dependency");
      for (int i = 0; i < deps.getLength(); i++) {
        Node n = deps.item(i);
        if (!(n instanceof Element)) {
          continue;
        }
        Element e = (Element) n;
        String group = textOf(e, "groupId");
        String artifact = textOf(e, "artifactId");
        String version = textOf(e, "version");
        if (group != null && artifact != null && version != null && !version.startsWith("${")) {
          out.add(new Dependency("Maven", group + ":" + artifact, version));
        }
      }
    } catch (ParserConfigurationException | SAXException | IOException e) {
      // Match Python's silent skip for unparseable dependency files.
      return Collections.emptyList();
    }
    return out;
  }

  /** Parse a Gradle {@code build.gradle(.kts)} for literal {@code "g:a:v"} declarations. */
  static List<Dependency> parseGradleBuildScript(Path script) {
    List<Dependency> out = new ArrayList<>();
    try {
      String content = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
      Matcher m = GAV.matcher(content);
      while (m.find()) {
        out.add(new Dependency("Maven", m.group(1) + ":" + m.group(2), m.group(3)));
      }
    } catch (IOException e) {
      return Collections.emptyList();
    }
    return out;
  }

  private static String textOf(Element parent, String tag) {
    NodeList list = parent.getElementsByTagName(tag);
    if (list.getLength() == 0) {
      return null;
    }
    Node node = list.item(0);
    String text = node.getTextContent();
    return text == null ? null : text.trim();
  }
}
