package com.example.timsort.io;

import java.io.PrintStream;

/**
 * Abstraction over console input and output.
 *
 * <p>Decouples the menu logic from real I/O, allowing tests to substitute
 * a fake implementation and emulate user input without touching the
 * console.</p>
 */
public interface ConsoleIO {

  /**
   * Reads a single line from the input.
   *
   * @return the line entered by the user, or {@code null} if the input
   *         is exhausted
   */
  String readLine();

  /**
   * Prints a message to the output without a trailing newline.
   *
   * @param message the text to print
   */
  void print(String message);

  /**
   * Prints a message to the output with a trailing newline.
   *
   * @param message the text to print
   */
  void println(String message);

  /**
   * Prints a formatted message to the output.
   *
   * @param format the format string (see {@link String#format})
   * @param args   the arguments referenced by the format specifiers
   */
  void printf(String format, Object... args);

  /**
   * Returns this console as a PrintStream, for components that print
   * diagnostics through a stream API (e.g. FileBusSource warnings).
   *
   * <p>WHY: the application output must flow through ONE channel — the
   * injected ConsoleIO — no matter which API the component prefers.
   * Without this adapter, stream-based components silently fall back
   * to System.out and their warnings become invisible to tests and to
   * any non-console ConsoleIO implementation.</p>
   *
   * @return a PrintStream view of this console; every write goes to
   *         {@link #print(String)} / {@link #println(String)}
   */
  default PrintStream asPrintStream() {
    // WHY an anonymous subclass: PrintStream needs an OutputStream, but
    // the real destination is this console's methods — so the stream
    // itself writes nowhere and the overridden print methods delegate.
    return new PrintStream(java.io.OutputStream.nullOutputStream(), true) {
      @Override
      public void print(String s) {
        ConsoleIO.this.print(s == null ? "" : s);
      }

      @Override
      public void println(String s) {
        ConsoleIO.this.println(s == null ? "" : s);
      }
    };
  }
}