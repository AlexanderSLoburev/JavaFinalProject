package com.example.timsort.io;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Menu engine for the console application.
 *
 * <p>Holds a registry of commands keyed by menu option number, prints the menu
 * in an infinite loop, reads user input, and executes the corresponding command.
 * The loop terminates only when a registered command throws {@link ExitException}.</p>
 *
 * <p>Invalid input (non-numeric or unknown option) prints an error message and
 * continues the loop.</p>
 */
public class ConsoleMenu {

    private final ConsoleIO io;
    private final Session session;
    private final Map<Integer, Command> commands = new LinkedHashMap<>();
    private final Map<Integer, String> descriptions = new LinkedHashMap<>();

    /**
     * Creates a new menu with the given I/O and session.
     *
     * @param io      the console I/O abstraction, must not be null
     * @param session the shared session state, must not be null
     */
    public ConsoleMenu(ConsoleIO io, Session session) {
        this.io = io;
        this.session = session;
    }

    /**
     * Registers a menu option.
     *
     * <p>The {@code description} is printed in the menu; the {@code command}
     * is executed when the user chooses {@code key}. Registration order is
     * preserved when printing the menu.</p>
     *
     * @param key         the option number
     * @param description the label shown in the menu
     * @param command     the action to execute
     */
    public void register(int key, String description, Command command) {
        commands.put(key, command);
        descriptions.put(key, description);
    }

    /**
     * Runs the menu loop until {@link ExitException} is thrown.
     *
     * <p>On each iteration prints the menu, reads the user's choice, and executes
     * the matching command. If input is not a number, prints an error and continues.
     * If the option is unknown, prints an error and continues.</p>
     *
     * @throws ExitException when a registered command signals exit
     */
    public void run() {
        while (true) {
            printMenu();
            try {
                int choice = readChoice();
                Command command = commands.get(choice);
                if (command == null) {
                    io.print("Error: invalid menu option\n");
                } else {
                    command.execute();
                }
            } catch (NumberFormatException e) {
                io.print("Error: please enter a number\n");
            }
        }
    }

    private void printMenu() {
        io.print("Menu:\n");
        for (Map.Entry<Integer, String> entry : descriptions.entrySet()) {
            io.print(entry.getKey() + ". " + entry.getValue() + "\n");
        }
        io.print("Choose an option: ");
    }

    private int readChoice() {
        String line = io.readLine();
        if (line == null) {
            throw new ExitException();
        }
        return Integer.parseInt(line.trim());
    }
}