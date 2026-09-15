package com.example.timsort.app.commands;

import com.example.timsort.app.AppConfig;
import com.example.timsort.app.ConsolePrompter;
import com.example.timsort.model.Bus;
import java.util.List;

/**
 * Menu command 8: reads a target bus and counts its occurrences in the
 * current collection.
 */
public final class CountOccurrencesCommand extends AbstractSessionCommand {

  private final ConsolePrompter prompter;

  public CountOccurrencesCommand(AppConfig config) {
    super(config);
    this.prompter = new ConsolePrompter(config.console());
  }

  @Override
  protected void withCollection(List<Bus> collection) {
    var console = config.console();
    console.println("Enter bus data to search:");
    console.print("Route number: ");
    int route =
        prompter.readInt(1, 999); // keep in sync with BusValidator ranges
    console.print("Model: ");
    String model = console.readLine();
    console.print("Mileage: ");
    long mileage = prompter.readLong();

    Bus target =
        Bus.builder().routeNumber(route).model(model).mileage(mileage).build();

    long count = config.occurrenceCounter().count(collection, target);
    console.println("Occurrences found: " + count);
  }
}