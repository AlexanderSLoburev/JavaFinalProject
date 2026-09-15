package com.example.timsort.app.commands;

import com.example.timsort.app.AppConfig;
import com.example.timsort.app.ConsoleMenu;
import com.example.timsort.model.Bus;
import java.util.List;

/**
 * Base class for commands operating on the session state.
 *
 * <p>WHY a base class: the "collection is empty" guard repeats in five
 * commands; a template method keeps the message and the flow in one
 * place (the same anti-copy-paste rule as BusValidationConstants).</p>
 */
public abstract class AbstractSessionCommand implements Command {

  protected final AppConfig config;

  protected AbstractSessionCommand(AppConfig config) { this.config = config; }

  @Override
  public final void execute(ConsoleMenu menu) {
    config.session().getCurrent().ifPresentOrElse(this::withCollection,
                                                  this::onEmptyCollection);
  }

  /** Runs when the collection is present. */
  protected abstract void withCollection(List<Bus> collection);

  /** Runs when no collection has been filled yet. */
  protected void onEmptyCollection() {
    config.console().println("Collection is empty. Fill it first.");
  }
}