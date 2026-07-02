package com.integrallis.mfcqi.security.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrallis.mfcqi.security.SecurityFinding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BytecodeSecurityScannerTest {

  @Test
  void detectsSecurityIssuesInCompiledBytecode(@TempDir Path dir) throws Exception {
    Path src = dir.resolve("Vuln.java");
    Files.writeString(
        src,
        "import java.security.MessageDigest;\n"
            + "import java.util.Random;\n"
            + "public class Vuln {\n"
            + "  public byte[] hash(byte[] d) throws Exception {\n"
            + "    return MessageDigest.getInstance(\"MD5\").digest(d);\n"
            + "  }\n"
            + "  public int token() { return new Random().nextInt(); }\n"
            + "}\n");

    Path classes = dir.resolve("classes");
    Files.createDirectories(classes);
    int rc =
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classes.toString(), src.toString());
    assertThat(rc).isZero();

    List<SecurityFinding> findings =
        new BytecodeSecurityScanner(List.of(classes), List.of()).scan(dir);

    // FindSecBugs should flag the weak MD5 digest and/or the predictable Random.
    assertThat(findings).as("SpotBugs+FindSecBugs security findings").isNotEmpty();
    assertThat(findings)
        .anySatisfy(f -> assertThat(f.cweId()).startsWith("CWE-").isNotEqualTo("CWE-0"));
  }

  @Test
  void emptyClassDirsProduceNoFindings(@TempDir Path dir) {
    assertThat(new BytecodeSecurityScanner(List.of(), List.of()).scan(dir)).isEmpty();
  }
}
