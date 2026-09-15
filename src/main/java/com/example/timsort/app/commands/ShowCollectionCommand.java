package com.example.timsort.app.commands;

import com.example.timsort.app.AppConfig;
import com.example.timsort.app.ConsoleMenu;
import com.example.timsort.app.commands.Command;
import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.io.ConsoleIO;
import com.example.timsort.model.Bus;
import java.util.List;

/**
 * Menu command 2: prints the current collection AND the last
 * operation result, clearly labelled.
 *
 * <p>WHY both slots: sorting writes to lastResult and does not touch
 * the current collection — showing only the current one made the
 * sorted data invisible ("I sorted it, but Show prints the same
 * order"). Now the command reports both states explicitly.</p>
 */
public final class ShowCollectionCommand implements Command {

  private final AppConfig config;

  public ShowCollectionCommand(AppConfig config) { this.config = config; }

  @Override
  public void execute(ConsoleMenu menu) {
    var console = config.console();
    printSlot(console, "\nCurrent collection",
              config.session().getCurrent().orElse(null));
    printSlot(console, "\nLast result",
              config.session().getLastResult().orElse(null));
  }

  private void printSlot(ConsoleIO console, String title, List<Bus> items) {
    if (items == null) {
      console.println(title + ": not set yet.");
      return;
    }
    console.println(title + " (" + items.size() + " elements):");
    for (Bus bus : items) {
      console.println(bus.toString());
    }
  }
}