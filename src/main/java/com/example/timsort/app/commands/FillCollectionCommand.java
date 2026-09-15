package com.example.timsort.app.commands;

import com.example.timsort.app.AppConfig;
import com.example.timsort.app.ConsoleMenu;
import com.example.timsort.app.ConsolePrompter;
import com.example.timsort.model.Bus;
import com.example.timsort.source.DataSource;

/**
 * Menu command 1: asks for a source and a count, fills the session.
 */
public final class FillCollectionCommand implements Command {

  private static final int RANDOM_SOURCE = 1;
  private static final int FILE_SOURCE = 2;
  private static final int MANUAL_SOURCE = 3;

  private final AppConfig config;
  private final ConsolePrompter prompter;

  public FillCollectionCommand(AppConfig config) {
    this.config = config;
    this.prompter = new ConsolePrompter(config.console());
  }

  @Override
  public void execute(ConsoleMenu menu) {
    var console = config.console();
    console.println("Select source:");
    console.println("1. Random generation");
    console.println("2. From file (buses.csv)");
    console.println("3. Manual input");
    console.print("Your choice: ");

    int sourceChoice = prompter.readInt(RANDOM_SOURCE, MANUAL_SOURCE);
    console.print("Enter number of elements: ");
    int count = prompter.readPositiveInt();

    DataSource<Bus> source = switch (sourceChoice) {
      case FILE_SOURCE -> config.fileSource();
      case MANUAL_SOURCE -> config.manualSource();
      default -> config.randomSource();
    };

    var collection = source.provide(count);
    config.session().setCurrent(collection);
    console.println("Collection filled: " + collection.size() + " elements.");
  }
}