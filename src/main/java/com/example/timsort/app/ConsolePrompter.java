package com.example.timsort.app;

import com.example.timsort.io.ConsoleIO;
import java.util.Objects;


/**
 * Console number-reading helpers with retry loops.
 *
 * <p>Extracted from Application: the loops are testable behavior
 * (invalid input -> message -> retry), while the Application itself
 * stays a thin composition root.</p>
 */
public final class ConsolePrompter {

  private final ConsoleIO console;

  public ConsolePrompter(ConsoleIO console) {
    this.console = Objects.requireNonNull(console, "console must not be null");
  }

  /**
   * Reads an integer within [min, max], retrying on invalid input.
   */
  public int readInt(int min, int max) {
    while (true) {
      try {
        int value = Integer.parseInt(console.readLine().trim());
        if (value >= min && value <= max) {
          return value;
        }
        console.println("Enter a number from " + min + " to " + max);
      } catch (NumberFormatException e) {
        console.println("Invalid input. Please enter an integer.");
      }
    }
  }

  /**
   * Reads a positive integer, retrying on invalid input.
   */
  public int readPositiveInt() {
    while (true) {
      try {
        int value = Integer.parseInt(console.readLine().trim());
        if (value > 0) {
          return value;
        }
        console.println("Enter a positive number.");
      } catch (NumberFormatException e) {
        console.println("Invalid input. Please enter an integer.");
      }
    }
  }

  /**
   * Reads a long, retrying on invalid input.
   */
  public long readLong() {
    while (true) {
      try {
        return Long.parseLong(console.readLine().trim());
      } catch (NumberFormatException e) {
        console.println("Invalid input. Please enter an integer.");
      }
    }
  }
}