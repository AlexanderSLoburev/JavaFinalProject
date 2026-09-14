package com.example.timsort.io;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Menu engine for the console application.
 *
 * <p>Holds a registry of commands keyed by menu option number, prints the
 * menu in a loop, reads user input, and executes the corresponding
 * command. The loop stops when a command (or an EOF on input) requests
 * exit via {@link #requestExit()}.</p>
 *
 * <p>Invalid input (non-numeric or unknown option) prints an error message
 * and continues the loop.</p>
 *
 * <p>Thread model: the menu loop is single-threaded; {@link #requestExit()}
 * is the only thread-safe member, so an exit may also be requested from
 * another thread.</p>
 */
public class ConsoleMenu {

  /** Returned by readChoice when the user input is not a number. */
  private static final int INVALID_CHOICE = Integer.MIN_VALUE;

  /** A registered option: its label and its action. */
  private record CommandEntry(String description, Command command) {
  }

  private final ConsoleIO io;
  private final Map<Integer, CommandEntry> options = new LinkedHashMap<>();

  /**
   * WHY volatile: requestExit() may be called from another thread,
   * and the menu loop must observe it without a lock.
   */
  private volatile boolean exitRequested = false;

  /**
   * Creates a new menu.
   *
   * @param io the console I/O abstraction; must not be null
   */
  public ConsoleMenu(ConsoleIO io) {
    this.io = Objects.requireNonNull(io, "io must not be null");
  }

  /**
   * Requests the menu loop to stop after the current iteration.
   * Safe to call from any thread, including from a command.
   */
  public void requestExit() { exitRequested = true; }

  /**
   * Registers a menu option.
   *
   * <p>The {@code description} is printed in the menu; the {@code command}
   * is executed when the user chooses {@code key}. Registration order is
   * preserved when printing the menu.</p>
   *
   * @param key         the option number
   * @param description the label shown in the menu; must not be null
   * @param command      the action to execute; must not be null
   * @throws IllegalArgumentException if the key is already registered
   * @throws NullPointerException     if description or command is null
   */
  public void register(int key, String description, Command command) {
    if (options.containsKey(key)) {
      throw new IllegalArgumentException("Option already registered: " + key);
    }
    Objects.requireNonNull(description, "description must not be null");
    Objects.requireNonNull(command, "command must not be null");
    options.put(key, new CommandEntry(description, command));
  }

  /**
   * Runs the menu loop until exit is requested: prints the menu, reads the
   * user's choice, executes the matching command. Non-numeric input and
   * unknown options are reported and skipped.
   */
  public void run() {
    while (!exitRequested) {
      printMenu();
      int choice = readChoice();

      if (exitRequested) {
        // EOF (or a concurrent exit request): leave without an
        // error message — the input is closed, not wrong
        break;
      }

      if (choice == INVALID_CHOICE) {
        io.println("Error: please enter a number");
        continue;
      }

      CommandEntry entry = options.get(choice);
      if (entry == null) {
        io.println("Error: invalid menu option");
        continue;
      }

      entry.command().execute(this);
    }
  }

  private void printMenu() {
    io.println("Menu:");
    for (Map.Entry<Integer, CommandEntry> entry : options.entrySet()) {
      io.println(entry.getKey() + ". " + entry.getValue().description());
    }
    io.print("Choose an option: ");
  }

  /**
   * Reads the user's choice; EOF requests exit.
   *
   * @return the chosen option number, or {@link #INVALID_CHOICE} when the
   *         input is not a number
   */
  private int readChoice() {
    String line = io.readLine();
    if (line == null) {
      // EOF: stop the loop instead of spinning on an exhausted input
      requestExit();
      return INVALID_CHOICE;
    }

    try {
      return Integer.parseInt(line.trim());
    } catch (NumberFormatException e) {
      return INVALID_CHOICE;
    }
  }
}