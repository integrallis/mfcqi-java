package com.integrallis.mfcqi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class SpinnerTest {

  @Test
  void nonAnimated_printsOneStaticLine_andStopIsNoop() {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(buf, true, StandardCharsets.UTF_8);

    Spinner spinner = Spinner.start("Working...", err, false);
    spinner.stop(); // no thread to join; must not throw or clear
    spinner.stop(); // idempotent

    String out = buf.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("Working...");
    // No carriage-return animation or clear sequence in the non-TTY path.
    assertThat(out).doesNotContain("\r");
  }

  @Test
  void animated_writesProgressThenClearsOnStop() throws Exception {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(buf, true, StandardCharsets.UTF_8);

    Spinner spinner = Spinner.start("Querying model...", err, true);
    Thread.sleep(200); // let a couple of frames render
    spinner.stop();

    String out = buf.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("Querying model...");
    assertThat(out).contains("\r"); // animated frames rewrite the line
  }

  @Test
  void defaultStart_doesNotThrow() {
    // In the test JVM there is no console, so this exercises the non-animated branch.
    assertThatCode(
            () -> {
              Spinner s = Spinner.start("hello");
              s.stop();
            })
        .doesNotThrowAnyException();
  }
}
