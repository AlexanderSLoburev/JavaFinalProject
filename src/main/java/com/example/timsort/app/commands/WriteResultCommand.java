package com.example.timsort.app.commands;

import com.example.timsort.app.AppConfig;
import com.example.timsort.app.ConsoleMenu;

/**
 * Menu command 7: writes the last sort result to the results file.
 *
 * <p>WHY it extends nothing: unlike the others it operates on
 * {@code lastResult}, not on the current collection, so the
 * AbstractSessionCommand guard does not apply.</p>
 */
public final class WriteResultCommand implements Command {

  private final AppConfig config;

  public WriteResultCommand(AppConfig config) { this.config = config; }

  @Override
  public void execute(ConsoleMenu menu) {
    config.session().getLastResult().ifPresentOrElse(
        result
        -> {
          config.resultWriter().appendAll(result);
          config.console().println("Result written to file: " +
                                   config.resultsPath().toAbsolutePath());
        },
        ()
            -> config.console().println(
                "No result to write. Perform sorting first."));
  }
}