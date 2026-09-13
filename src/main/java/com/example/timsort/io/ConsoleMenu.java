package com.example.timsort.io;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConsoleMenu {

    private final ConsoleIO io;
    private final Session session;
    private final Map<Integer, Command> commands = new LinkedHashMap<>();
    private final Map<Integer, String> descriptions = new LinkedHashMap<>();

    public ConsoleMenu(ConsoleIO io, Session session) {
        this.io = io;
        this.session = session;
    }

    public void register(int key, String description, Command command) {
        commands.put(key, command);
        descriptions.put(key, description);
    }

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