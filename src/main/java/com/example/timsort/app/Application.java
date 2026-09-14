package com.example.timsort.app;

import com.example.timsort.codec.BusCodec;
import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.concurrent.OccurrenceCounter;
import com.example.timsort.concurrent.ParallelOccurrenceCounter;
import com.example.timsort.io.Command;
import com.example.timsort.io.ConsoleIO;
import com.example.timsort.io.ConsoleMenu;
import com.example.timsort.io.FileResultWriter;
import com.example.timsort.io.ResultWriter;
import com.example.timsort.io.Session;
import com.example.timsort.io.SystemConsoleIO;
import com.example.timsort.model.Bus;
import com.example.timsort.sort.BusField;
import com.example.timsort.sort.BusComparators;
import com.example.timsort.sort.ParitySorter;
import com.example.timsort.sort.Sorter;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composition root — the single place where all concrete implementations
 * are created and wired together.
 *
 * <p>Implements Dependency Injection: all concrete implementations are
 * created here and passed through constructors depending on abstractions
 * (interfaces). No other class in the application contains {@code new}
 * for concrete implementations (except simple ones).</p>
 *
 * <p>Follows SOLID principles:
 * <ul>
 *   <li>SRP — each class has one reason to change</li>
 *   <li>OCP — new commands, sources, sorters can be added without
 *       modifying existing code</li>
 *   <li>DIP — all dependencies point to abstractions</li>
 * </ul>
 * </p>
 */
public final class Application {

    /**
     * Private constructor prevents instantiation.
     * Application is a utility class with only static methods.
     */
    private Application() {
        // Instantiation prohibited
    }

    /**
     * Application entry point.
     *
     * <p>Creates all dependencies, registers menu commands, runs the menu
     * loop, and ensures proper shutdown of the executor service on exit.</p>
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        // Create dependencies
        ConsoleIO console = new SystemConsoleIO();
        Session session = new Session();
        Validator<Bus> validator = new BusValidator();
        BusCodec codec = new BusCodec();

        // Data sources (strategies)
        DataSource<Bus> randomSource = new RandomBusSource(validator);
        DataSource<Bus> fileSource = new FileBusSource(
                Paths.get("buses.csv"),
                codec,
                validator
        );
        DataSource<Bus> manualSource = new ManualBusSource(console, validator);

        // Sorting
        SorterFactory<Bus> sorterFactory = TimSorter::new;
        ParitySorter<Bus> paritySorter = new TimParitySorter<>(
                sorterFactory,
                Bus::routeNumber
        );

        // File writing
        Path resultsPath = Paths.get("results.txt");
        ResultWriter<Bus> resultWriter = new FileResultWriter<>(
                resultsPath,
                codec::encode
        );

        // Multi-threaded counting
        ExecutorService executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
        OccurrenceCounter<Bus> occurrenceCounter = new ParallelOccurrenceCounter<>(executor);

        // Menu
        ConsoleMenu menu = new ConsoleMenu(console);

        // Register commands as lambdas
        registerCommands(menu, console, session, randomSource, fileSource,
                manualSource, sorterFactory, paritySorter, resultWriter,
                resultsPath, occurrenceCounter);

        // Run menu loop (blocks until exit)
        menu.run();

        // Proper shutdown: close executor service after exit
        executor.shutdown();
    }

    /**
     * Registers all menu commands.
     *
     * @param menu             the menu to register commands in
     * @param console          console I/O abstraction
     * @param session          application state holder
     * @param randomSource     random data source
     * @param fileSource       file data source
     * @param manualSource     manual input data source
     * @param sorterFactory    factory for creating sorters
     * @param paritySorter     parity sorter instance
     * @param resultWriter     file result writer
     * @param resultsPath      path to results file
     * @param occurrenceCounter parallel occurrence counter
     */
    private static void registerCommands(
            ConsoleMenu menu,
            ConsoleIO console,
            Session session,
            DataSource<Bus> randomSource,
            DataSource<Bus> fileSource,
            DataSource<Bus> manualSource,
            SorterFactory<Bus> sorterFactory,
            ParitySorter<Bus> paritySorter,
            ResultWriter<Bus> resultWriter,
            Path resultsPath,
            OccurrenceCounter<Bus> occurrenceCounter
    ) {
        // Command 0: Exit
        menu.register(0, "Exit", m -> {
            console.println("Shutting down...");
            m.requestExit();
        });

        // Command 1: Fill collection
        menu.register(1, "Fill collection", m -> {
            console.println("Select source:");
            console.println("1. Random generation");
            console.println("2. From file (buses.csv)");
            console.println("3. Manual input");
            console.print("Your choice: ");

            int sourceChoice = readInt(console, 1, 3);
            console.print("Enter number of elements: ");
            int count = readPositiveInt(console);

            DataSource<Bus> source = switch (sourceChoice) {
                case 2 -> fileSource;
                case 3 -> manualSource;
                default -> randomSource;
            };

            CustomArrayList<Bus> collection = source.provide(count);
            session.setCurrent(collection);
            console.println("Collection filled: " + collection.size() + " elements.");
        });

        // Command 2: Show current collection
        menu.register(2, "Show current collection", m -> {
            session.getCurrent().ifPresentOrElse(
                    collection -> {
                        console.println("Current collection (" + collection.size() + " elements):");
                        for (Bus bus : collection) {
                            console.println(bus.toString());
                        }
                    },
                    () -> console.println("Collection is empty. Fill it first.")
            );
        });

        // Command 3: Sort by route number
        menu.register(3, "Sort by route number", m -> {
            session.getCurrent().ifPresentOrElse(
                    collection -> {
                        var comparator = BusComparators.byField(BusField.ROUTE_NUMBER);
                        var sorter = sorterFactory.create(collection, comparator);
                        var sorted = sorter.sort();
                        session.setLastResult(sorted);
                        console.println("Sorted by route number.");
                    },
                    () -> console.println("Collection is empty. Fill it first.")
            );
        });

        // Command 4: Sort by model
        menu.register(4, "Sort by model", m -> {
            session.getCurrent().ifPresentOrElse(
                    collection -> {
                        var comparator = BusComparators.byField(BusField.MODEL);
                        var sorter = sorterFactory.create(collection, comparator);
                        var sorted = sorter.sort();
                        session.setLastResult(sorted);
                        console.println("Sorted by model.");
                    },
                    () -> console.println("Collection is empty. Fill it first.")
            );
        });

        // Command 5: Sort by mileage
        menu.register(5, "Sort by mileage", m -> {
            session.getCurrent().ifPresentOrElse(
                    collection -> {
                        var comparator = BusComparators.byField(BusField.MILEAGE);
                        var sorter = sorterFactory.create(collection, comparator);
                        var sorted = sorter.sort();
                        session.setLastResult(sorted);
                        console.println("Sorted by mileage.");
                    },
                    () -> console.println("Collection is empty. Fill it first.")
            );
        });

        // Command 6: Parity sort
        menu.register(6, "Parity sort by route number", m -> {
            session.getCurrent().ifPresentOrElse(
                    collection -> {
                        var sorted = paritySorter.sort(collection);
                        session.setLastResult(sorted);
                        console.println("Parity sort completed.");
                    },
                    () -> console.println("Collection is empty. Fill it first.")
            );
        });

        // Command 7: Write to file
        menu.register(7, "Write last result to file", m -> {
            session.getLastResult().ifPresentOrElse(
                    result -> {
                        resultWriter.appendAll(result);
                        console.println("Result written to file: " + resultsPath.toAbsolutePath());
                    },
                    () -> console.println("No result to write. Perform sorting first.")
            );
        });

        // Command 8: Count occurrences
        menu.register(8, "Count element occurrences", m -> {
            session.getCurrent().ifPresentOrElse(
                    collection -> {
                        console.println("Enter bus data to search:");
                        console.print("Route number: ");
                        int route = readInt(console, 1, 999);
                        console.print("Model: ");
                        String model = console.readLine();
                        console.print("Mileage: ");
                        long mileage = readLong(console);

                        Bus target = Bus.builder()
                                .routeNumber(route)
                                .model(model)
                                .mileage(mileage)
                                .build();

                        long count = occurrenceCounter.count(collection, target);
                        console.println("Occurrences found: " + count);
                    },
                    () -> console.println("Collection is empty. Fill it first.")
            );
        });
    }

    /**
     * Reads an integer within the specified range [min, max].
     *
     * @param console console I/O abstraction
     * @param min     minimum allowed value (inclusive)
     * @param max     maximum allowed value (inclusive)
     * @return the validated integer
     */
    private static int readInt(ConsoleIO console, int min, int max) {
        while (true) {
            try {
                int value = Integer.parseInt(console.readLine().trim());
                if (value >= min && value <= max) {
                    return value;
                }
                console.println("Enter a number from " + min + " to " + max);
            } catch (NumberFormatException e) {
                console.println("Invalid input. Please enter an integer.");
            }
        }
    }

    /**
     * Reads a positive integer.
     *
     * @param console console I/O abstraction
     * @return the validated positive integer
     */
    private static int readPositiveInt(ConsoleIO console) {
        while (true) {
            try {
                int value = Integer.parseInt(console.readLine().trim());
                if (value > 0) {
                    return value;
                }
                console.println("Enter a positive number.");
            } catch (NumberFormatException e) {
                console.println("Invalid input. Please enter an integer.");
            }
        }
    }

    /**
     * Reads a long integer.
     *
     * @param console console I/O abstraction
     * @return the validated long integer
     */
    private static long readLong(ConsoleIO console) {
        while (true) {
            try {
                return Long.parseLong(console.readLine().trim());
            } catch (NumberFormatException e) {
                console.println("Invalid input. Please enter an integer.");
            }
        }
    }
}