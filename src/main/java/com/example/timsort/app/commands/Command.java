package com.example.timsort.app.commands;

import com.example.timsort.app.ConsoleMenu;

/**
 * A menu action.
 *
 * <p>Functional interface: implementations are typically provided as lambdas
 * or method references when registering menu options —
 * {@code menu.register(0, "Exit", ConsoleMenu::requestExit)}.</p>
 */
@FunctionalInterface
public interface Command {

  /**
   * Executes the action.
   *
   * @param menu the menu this command is registered in; never null
   */
  void execute(ConsoleMenu menu);
}