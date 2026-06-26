package com.integrallis.mfcqi.cli;

import java.io.PrintStream;

/**
 * Minimal console progress spinner for long-running steps (notably the LLM call, which may stall
 * for a while loading a local model). Writes to {@code stderr} so it never pollutes {@code stdout}
 * report content.
 *
 * <p>On an interactive terminal it animates a single rewriting line; when output is redirected (no
 * TTY) it prints one static line instead, so logs/pipes get a message without control-character
 * spam. {@link #stop()} is always safe to call and clears the animated line.
 */
public final class Spinner {

  private static final char CR = (char) 13; // carriage return
  private static final char ESC = (char) 27; // escape (for ANSI sequences)
  private static final String CLEAR_LINE = "" + CR + ESC + "[2K"; // CR + ANSI erase-line
  // Spinner frames (the last is a backslash, built from its code point to avoid escapes).
  private static final String[] FRAMES = {"|", "/", "-", "" + (char) 92};
  private static final long INTERVAL_MS = 120L;

  private final PrintStream err;
  private final Thread thread;
  private volatile boolean running = true;

  private Spinner(String message, PrintStream err, boolean animate) {
    this.err = err;
    if (!animate) {
      err.println(message);
      this.thread = null;
      return;
    }
    this.thread =
        new Thread(
            () -> {
              int i = 0;
              while (running) {
                err.print("" + CR + FRAMES[i++ % FRAMES.length] + " " + message);
                err.flush();
                try {
                  Thread.sleep(INTERVAL_MS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
              }
            },
            "mfcqi-spinner");
    this.thread.setDaemon(true);
    this.thread.start();
  }

  /**
   * Starts a spinner with the default policy: animate only on an interactive terminal ({@code
   * System.console() != null}), writing to {@code System.err}.
   *
   * @param message the text shown next to the spinner
   * @return a running spinner; call {@link #stop()} when the work completes
   */
  public static Spinner start(String message) {
    return new Spinner(message, System.err, System.console() != null);
  }

  /**
   * Starts a spinner with explicit control over the output stream and animation, for testing.
   *
   * @param message the text shown next to the spinner
   * @param err the stream to write to
   * @param animate {@code true} to animate on a thread, {@code false} to print one static line
   * @return a running spinner
   */
  static Spinner start(String message, PrintStream err, boolean animate) {
    return new Spinner(message, err, animate);
  }

  /** Stops the spinner (if animating) and clears its line. Safe to call more than once. */
  public void stop() {
    running = false;
    if (thread == null) {
      return;
    }
    thread.interrupt();
    try {
      thread.join(500L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    err.print(CLEAR_LINE);
    err.flush();
  }
}
