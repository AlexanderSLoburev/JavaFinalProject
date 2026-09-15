package com.example.timsort.app.commands;

import com.example.timsort.app.AppConfig;
import com.example.timsort.app.ConsoleMenu;

/**
 * Menu command 0: prints a farewell and requests the loop to stop.
 */
public final class ExitCommand implements Command {

  private final AppConfig config;

  public ExitCommand(AppConfig config) { this.config = config; }

  @Override
  public void execute(ConsoleMenu menu) {
    config.console().println("Shutting down...");
    menu.requestExit();
  }
}