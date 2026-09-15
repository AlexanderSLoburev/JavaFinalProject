package com.example.timsort.app.commands;

import com.example.timsort.app.AppConfig;
import com.example.timsort.model.Bus;
import com.example.timsort.sort.BusComparators;
import com.example.timsort.sort.BusField;
import java.util.List;

/**
 * Menu commands 3/4/5: sort the current collection by a single field.
 */
public final class SortByFieldCommand extends AbstractSessionCommand {

  private final BusField field;

  public SortByFieldCommand(AppConfig config, BusField field) {
    super(config);
    this.field = field;
  }

  @Override
  protected void withCollection(List<Bus> collection) {
    var sorter = config.sorterFactory().create(collection,
                                               BusComparators.byField(field));
    var sorted = sorter.sort();
    config.session().setLastResult(sorted);
    config.console().println("Sorted by " + field + ".");
  }
}