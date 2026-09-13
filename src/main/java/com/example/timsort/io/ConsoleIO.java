package com.example.timsort.io;

/**
 * Abstraction over console input and output.
 *
 * <p>Decouples the menu logic from real I/O, allowing tests to substitute
 * a mock implementation and emulate user input without touching the console.</p>
 */
public interface ConsoleIO {

    /**
     * Reads a single line from the input.
     *
     * @return the line entered by the user, or {@code null} if the input is exhausted
     */
    String readLine();

    /**
     * Prints a message to the output without a trailing newline.
     *
     * @param message the text to print
     */
    void print(String message);

    /**
     * Prints a formatted message to the output.
     *
     * @param format the format string (see {@link String#format})
     * @param args   the arguments referenced by the format specifiers
     */
    void printf(String format, Object... args);
}