package com.example.timsort.app.commands;

import com.example.timsort.app.AppConfig;
import com.example.timsort.model.Bus;
import java.util.List;


/**
 * Menu command 6: parity sort of the current collection by route number.
 */
public final class ParitySortCommand extends AbstractSessionCommand {

  public ParitySortCommand(AppConfig config) { super(config); }

  @Override
  protected void withCollection(List<Bus> collection) {
    var sorted = config.paritySorter().sort(collection);
    config.session().setLastResult(sorted);
    config.console().println("Parity sort completed.");
  }
}