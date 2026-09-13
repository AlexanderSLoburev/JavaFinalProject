package com.example.timsort.io;

/**
 * A menu action.
 *
 * <p>Functional interface: implementations are typically provided as lambdas
 * when registering menu options in {@link ConsoleMenu#register(int, String, Command)}.</p>
 */
@FunctionalInterface
public interface Command {

    /**
     * Executes the action.
     *
     * <p>May throw {@link ExitException} to signal that the menu loop should stop.</p>
     */
    void execute();
}