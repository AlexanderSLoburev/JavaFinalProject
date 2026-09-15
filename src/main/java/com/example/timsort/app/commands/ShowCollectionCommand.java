package com.example.timsort.app.commands;

import com.example.timsort.app.AppConfig;
import com.example.timsort.model.Bus;
import java.util.List;


/**
 * Menu command 2: prints the current collection.
 */
public final class ShowCollectionCommand extends AbstractSessionCommand {

  public ShowCollectionCommand(AppConfig config) { super(config); }

  @Override
  protected void withCollection(List<Bus> collection) {
    var console = config.console();
    console.println("Current collection (" + collection.size() + " elements):");
    for (Bus bus : collection) {
      console.println(bus.toString());
    }
  }
}