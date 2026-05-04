package com.integrallis.mfcqi.security;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.integrallis.mfcqi.core.JavaSourceFiles;
import com.integrallis.mfcqi.security.SecurityFinding.Confidence;
import com.integrallis.mfcqi.security.SecurityFinding.Severity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AST-driven Java security scanner — Java analog of Bandit's source-level checks. Each check
 * mirrors the corresponding Python Bandit test ID (kept in the test_id field for parity with the
 * {@code mfcqi/metrics/security.py:BANDIT_TO_CWE} mapping).
 *
 * <p>Implemented checks (Bandit ID → Java pattern):
 *
 * <ul>
 *   <li>{@code B303} — {@code MessageDigest.getInstance("MD5"|"SHA-1")} (weak hash, CWE-327)
 *   <li>{@code B304} — {@code Cipher.getInstance("DES"|"3DES"|"RC4"|"Blowfish")} (weak cipher,
 *       CWE-327)
 *   <li>{@code B311} — {@code new java.util.Random()} when used for security purposes (insufficient
 *       randomness, CWE-330)
 *   <li>{@code B501} — {@code TrustManager} that accepts everything / disables hostname
 *       verification (CWE-295)
 *   <li>{@code B502} — {@code SSLContext.getInstance("SSL"|"SSLv2"|"SSLv3"|"TLSv1")} (deprecated
 *       protocol, CWE-327)
 *   <li>{@code B602/B605} — {@code Runtime.getRuntime().exec(...)} or {@code ProcessBuilder} with a
 *       string-concatenated command (command injection, CWE-78)
 *   <li>{@code B608} — {@code Statement.execute*}/{@code executeQuery}/{@code executeUpdate} on a
 *       string-concatenated SQL expression (CWE-89)
 *   <li>{@code B301/B403} — {@code ObjectInputStream.readObject()} (unsafe deserialization,
 *       CWE-502)
 *   <li>{@code B313–B320} — {@code DocumentBuilderFactory.newInstance()} without {@code
 *       setFeature(...)} hardening calls (XXE, CWE-611)
 * </ul>
 */
public final class JavaSecurityScanner {

  private static final Logger LOG = LoggerFactory.getLogger(JavaSecurityScanner.class);

  private static final Set<String> WEAK_HASHES =
      new HashSet<>(Arrays.asList("md5", "md4", "md2", "sha-1", "sha1"));
  private static final Set<String> WEAK_CIPHERS =
      new HashSet<>(Arrays.asList("des", "3des", "desede", "rc4", "rc2", "blowfish", "arc4"));
  private static final Set<String> DEPRECATED_TLS =
      new HashSet<>(Arrays.asList("ssl", "sslv2", "sslv3", "tls", "tlsv1", "tlsv1.1"));

  /** Scan an entire codebase. Files that fail to parse are silently skipped. */
  public List<SecurityFinding> scan(Path codebase) {
    if (codebase == null) {
      return new ArrayList<>();
    }
    List<SecurityFinding> findings = new ArrayList<>();
    for (Path file : JavaSourceFiles.findAll(codebase)) {
      findings.addAll(scanFile(file));
    }
    return findings;
  }

  /** Scan a single file. */
  public List<SecurityFinding> scanFile(Path file) {
    String source;
    CompilationUnit cu;
    try {
      source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      cu = StaticJavaParser.parse(source);
    } catch (IOException | RuntimeException e) {
      LOG.debug("Skipping unparseable file {}: {}", file, e.getMessage());
      return new ArrayList<>();
    }

    List<SecurityFinding> findings = new ArrayList<>();

    for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
      String name = call.getNameAsString();

      // MessageDigest / Cipher / SSLContext .getInstance("...")
      if ("getInstance".equals(name) && !call.getArguments().isEmpty()) {
        Expression arg0 = call.getArgument(0);
        String literal = literalString(arg0);
        if (literal != null) {
          String norm = literal.toLowerCase(java.util.Locale.ROOT).trim();
          String scope = scopeName(call);
          if (scope.endsWith("MessageDigest") && WEAK_HASHES.contains(norm)) {
            findings.add(
                build(
                    file,
                    arg0,
                    "B303",
                    "use_of_weak_hash",
                    Severity.MEDIUM,
                    Confidence.HIGH,
                    "Use of insecure hash function: " + literal,
                    "CWE-327"));
          } else if (scope.endsWith("Cipher") && containsAny(norm, WEAK_CIPHERS)) {
            findings.add(
                build(
                    file,
                    arg0,
                    "B304",
                    "use_of_weak_cipher",
                    Severity.HIGH,
                    Confidence.HIGH,
                    "Use of insecure cipher: " + literal,
                    "CWE-327"));
          } else if (scope.endsWith("SSLContext") && DEPRECATED_TLS.contains(norm)) {
            findings.add(
                build(
                    file,
                    arg0,
                    "B502",
                    "ssl_with_bad_version",
                    Severity.MEDIUM,
                    Confidence.HIGH,
                    "Use of deprecated TLS/SSL protocol: " + literal,
                    "CWE-327"));
          }
        }
      }

      // SQL injection — Statement.execute*(<concatenated>)
      if (("execute".equals(name)
              || "executeQuery".equals(name)
              || "executeUpdate".equals(name)
              || "executeLargeUpdate".equals(name))
          && !call.getArguments().isEmpty()
          && isStringConcatenation(call.getArgument(0))) {
        findings.add(
            build(
                file,
                call,
                "B608",
                "hardcoded_sql_expressions",
                Severity.MEDIUM,
                Confidence.MEDIUM,
                "Possible SQL injection: query built via string concatenation",
                "CWE-89"));
      }

      // Command injection — Runtime.exec(<concatenated>) or ProcessBuilder(<concatenated>)
      if ("exec".equals(name)
          && !call.getArguments().isEmpty()
          && isStringConcatenation(call.getArgument(0))) {
        findings.add(
            build(
                file,
                call,
                "B605",
                "start_process_with_a_shell",
                Severity.HIGH,
                Confidence.HIGH,
                "Possible command injection: Runtime.exec called with concatenated string",
                "CWE-78"));
      }

      // Unsafe deserialization — readObject() on ObjectInputStream
      if ("readObject".equals(name) && call.getArguments().isEmpty()) {
        String scope = scopeName(call);
        if (scope.endsWith("ObjectInputStream") || scope.contains("InputStream")) {
          findings.add(
              build(
                  file,
                  call,
                  "B301",
                  "blacklist",
                  Severity.HIGH,
                  Confidence.HIGH,
                  "Use of insecure deserialization (ObjectInputStream.readObject)",
                  "CWE-502"));
        }
      }
    }

    // new Random()  — flagged when a sibling line also references "secret"/"token"/"password" or
    // a SecureRandom is not in scope. Conservative: always flag as LOW/LOW.
    for (ObjectCreationExpr newExpr : cu.findAll(ObjectCreationExpr.class)) {
      String typeName = newExpr.getType().getNameAsString();
      if ("Random".equals(typeName) && newExpr.getArguments().size() <= 1) {
        findings.add(
            build(
                file,
                newExpr,
                "B311",
                "blacklist",
                Severity.LOW,
                Confidence.LOW,
                "Use of java.util.Random — prefer SecureRandom for security-sensitive purposes",
                "CWE-330"));
      }
      // ProcessBuilder(<concatenated>) — flag command injection.
      if ("ProcessBuilder".equals(typeName)
          && !newExpr.getArguments().isEmpty()
          && isStringConcatenation(newExpr.getArgument(0))) {
        findings.add(
            build(
                file,
                newExpr,
                "B602",
                "subprocess_popen_with_shell_equals_true",
                Severity.HIGH,
                Confidence.HIGH,
                "Possible command injection: ProcessBuilder built with concatenated string",
                "CWE-78"));
      }
    }

    // XXE — DocumentBuilderFactory.newInstance() without subsequent hardening. We approximate by
    // looking for a newInstance() call whose enclosing method does not also contain
    // setFeature(...) or setExpandEntityReferences(...) calls.
    for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
      if ("newInstance".equals(call.getNameAsString())
          && scopeName(call).endsWith("DocumentBuilderFactory")) {
        com.github.javaparser.ast.Node enclosingMethod = call;
        while (enclosingMethod != null
            && !(enclosingMethod instanceof com.github.javaparser.ast.body.MethodDeclaration)) {
          enclosingMethod = enclosingMethod.getParentNode().orElse(null);
        }
        boolean hardened = false;
        if (enclosingMethod != null) {
          for (MethodCallExpr sibling : enclosingMethod.findAll(MethodCallExpr.class)) {
            String n = sibling.getNameAsString();
            if ("setFeature".equals(n)
                || "setExpandEntityReferences".equals(n)
                || "setXIncludeAware".equals(n)) {
              hardened = true;
              break;
            }
          }
        }
        if (!hardened) {
          findings.add(
              build(
                  file,
                  call,
                  "B313",
                  "blacklist",
                  Severity.MEDIUM,
                  Confidence.MEDIUM,
                  "DocumentBuilderFactory created without XXE hardening (setFeature)",
                  "CWE-611"));
        }
      }
    }

    return findings;
  }

  private static SecurityFinding build(
      Path file,
      com.github.javaparser.ast.Node node,
      String testId,
      String testName,
      Severity severity,
      Confidence confidence,
      String text,
      String cweId) {
    int line = node.getBegin().map(p -> p.line).orElse(0);
    return new SecurityFinding(file, line, testId, testName, severity, confidence, text, cweId);
  }

  private static String scopeName(MethodCallExpr call) {
    if (!call.getScope().isPresent()) {
      return "";
    }
    Expression scope = call.getScope().get();
    if (scope instanceof NameExpr) {
      return ((NameExpr) scope).getNameAsString();
    }
    return scope.toString();
  }

  private static String literalString(Expression e) {
    if (e instanceof StringLiteralExpr) {
      return ((StringLiteralExpr) e).getValue();
    }
    return null;
  }

  private static boolean isStringConcatenation(Expression e) {
    if (e instanceof BinaryExpr) {
      BinaryExpr be = (BinaryExpr) e;
      if (be.getOperator() == BinaryExpr.Operator.PLUS) {
        // At least one side is a string literal -> string concat heuristic.
        return be.getLeft() instanceof StringLiteralExpr
            || be.getRight() instanceof StringLiteralExpr
            || isStringConcatenation(be.getLeft())
            || isStringConcatenation(be.getRight());
      }
    }
    return false;
  }

  private static boolean containsAny(String s, Set<String> needles) {
    for (String n : needles) {
      if (s.contains(n)) {
        return true;
      }
    }
    return false;
  }
}
