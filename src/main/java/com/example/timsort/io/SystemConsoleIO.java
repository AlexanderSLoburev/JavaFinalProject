package com.example.timsort.io;

import java.util.Scanner;

/**
 * Implementation of {@link ConsoleIO} for working with the real console.
 *
 * <p>Uses {@link Scanner} for reading from {@code System.in} and
 * {@code System.out} for output.</p>
 */
public class SystemConsoleIO implements ConsoleIO {

    private final Scanner scanner;

    /**
     * Creates a new {@code SystemConsoleIO} instance.
     *
     * <p>Initializes a {@link Scanner} for reading from {@code System.in}.</p>
     */
    public SystemConsoleIO() {
        this.scanner = new Scanner(System.in);
    }

    @Override
    public String readLine() {
        return scanner.nextLine();
    }

    @Override
    public void print(String message) {
        System.out.print(message);
    }

    @Override
    public void printf(String format, Object... args) {
        System.out.printf(format, args);
    }

    /**
     * Prints a message followed by a newline.
     *
     * @param message the message to print
     */
    public void println(String message) {
        System.out.println(message);
    }
}