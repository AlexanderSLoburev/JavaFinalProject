package com.example.timsort.app;

import com.example.timsort.app.commands.CountOccurrencesCommand;
import com.example.timsort.app.commands.ExitCommand;
import com.example.timsort.app.commands.FillCollectionCommand;
import com.example.timsort.app.commands.ParitySortCommand;
import com.example.timsort.app.commands.ShowCollectionCommand;
import com.example.timsort.app.commands.SortByFieldCommand;
import com.example.timsort.app.commands.WriteResultCommand;
import com.example.timsort.codec.BusCodec;
import com.example.timsort.concurrent.OccurrenceCounter;
import com.example.timsort.concurrent.ParallelOccurrenceCounter;
import com.example.timsort.io.ConsoleIO;
import com.example.timsort.io.FileResultWriter;
import com.example.timsort.io.ResultWriter;
import com.example.timsort.io.Session;
import com.example.timsort.io.SystemConsoleIO;
import com.example.timsort.model.Bus;
import com.example.timsort.sort.BusField;
import com.example.timsort.sort.ParitySorter;
import com.example.timsort.sort.SorterFactory;
import com.example.timsort.sort.TimParitySorter;
import com.example.timsort.sort.TimSorter;
import com.example.timsort.source.DataSource;
import com.example.timsort.source.FileBusSource;
import com.example.timsort.source.ManualBusSource;
import com.example.timsort.source.RandomBusSource;
import com.example.timsort.validation.BusValidator;
import com.example.timsort.validation.Validator;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Composition root — the single place where all concrete implementations
 * are created and wired together.
 *
 * <p>All commands are separate classes (see the commands package) wired
 * here through an AppConfig record; the menu registration is a plain
 * declaration list.</p>
 */
public final class Application {

  private static final Path DEFAULT_BUSES_PATH = Paths.get("buses.csv");
  private static final Path DEFAULT_RESULTS_PATH = Paths.get("results.txt");

  private Application() {
    // Instantiation prohibited
  }

  /**
   * Application entry point: wires the production configuration.
   *
   * @param args command-line arguments (not used)
   */
  public static void main(String[] args) {
    run(DEFAULT_BUSES_PATH, DEFAULT_RESULTS_PATH, new SystemConsoleIO());
  }

  /**
   * Runs the whole application with the given configuration.
   *
   * <p>Package-private on purpose: integration tests drive the app
   * through this entry point with a fake console and temp files.</p>
   *
   * @param busesPath    path to the input CSV file
   * @param resultsPath  path to the results output file
   * @param console      console I/O
   */
  static void run(Path busesPath, Path resultsPath, ConsoleIO console) {
    Validator<Bus> validator = new BusValidator();
    BusCodec codec = new BusCodec();

    DataSource<Bus> randomSource = new RandomBusSource(validator);
    DataSource<Bus> fileSource =
        new FileBusSource(busesPath, codec, validator, console.asPrintStream());
    DataSource<Bus> manualSource = new ManualBusSource(console, validator);

    SorterFactory<Bus> sorterFactory = TimSorter::new;

    ParitySorter<Bus> paritySorter =
        new TimParitySorter<>(sorterFactory, Bus::routeNumber);

    ResultWriter<Bus> resultWriter =
        new FileResultWriter<>(resultsPath, codec::encode);

    ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors());
    try {
      OccurrenceCounter<Bus> occurrenceCounter =
          new ParallelOccurrenceCounter<>(executor);

      ConsoleMenu menu = new ConsoleMenu(console);
      registerCommands(
          menu, new AppConfig(console, new Session(), randomSource, fileSource,
                              manualSource, sorterFactory, paritySorter,
                              resultWriter, resultsPath, occurrenceCounter));

      // A command throwing a RuntimeException must
      // not leave the executor running
      menu.run();
    } finally {
      shutdown(executor);
    }
  }

  private static void registerCommands(ConsoleMenu menu, AppConfig config) {
    menu.register(0, "Exit", new ExitCommand(config));
    menu.register(1, "Fill collection", new FillCollectionCommand(config));
    menu.register(2, "Show current collection",
                  new ShowCollectionCommand(config));
    menu.register(3, "Sort by route number",
                  new SortByFieldCommand(config, BusField.ROUTE_NUMBER));
    menu.register(4, "Sort by model",
                  new SortByFieldCommand(config, BusField.MODEL));
    menu.register(5, "Sort by mileage",
                  new SortByFieldCommand(config, BusField.MILEAGE));
    menu.register(6, "Parity sort by route number",
                  new ParitySortCommand(config));
    menu.register(7, "Write last result to file",
                  new WriteResultCommand(config));
    menu.register(8, "Count element occurrences",
                  new CountOccurrencesCommand(config));
  }

  /**
   * Graceful shutdown: regular attempt, then forced.
   */
  private static void shutdown(ExecutorService executor) {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}