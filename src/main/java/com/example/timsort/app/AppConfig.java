package com.example.timsort.app;

import com.example.timsort.concurrent.OccurrenceCounter;
import com.example.timsort.io.ConsoleIO;
import com.example.timsort.io.ResultWriter;
import com.example.timsort.io.Session;
import com.example.timsort.model.Bus;
import com.example.timsort.sort.ParitySorter;
import com.example.timsort.sort.SorterFactory;
import com.example.timsort.source.DataSource;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Wiring configuration handed over to the application commands.
 *
 * <p>Commands declare only the dependencies they actually use, and adding a new
 * shared dependency does not change any signature.</p>
 *
 * @param console          console I/O
 * @param session          shared application state
 * @param randomSource     random data source
 * @param fileSource       file data source
 * @param manualSource     manual input data source
 * @param sorterFactory    factory for TimSort-based sorters
 * @param paritySorter     parity sorter by route number
 * @param resultWriter     writer of the last result
 * @param resultsPath      where the results are written (for messages)
 * @param occurrenceCounter parallel occurrence counter
 */
public record
    AppConfig(ConsoleIO console, Session session, DataSource<Bus> randomSource,
              DataSource<Bus> fileSource, DataSource<Bus> manualSource,
              SorterFactory<Bus> sorterFactory, ParitySorter<Bus> paritySorter,
              ResultWriter<Bus> resultWriter, Path resultsPath,
              OccurrenceCounter<Bus> occurrenceCounter) {

  public AppConfig {
    Objects.requireNonNull(console, "console must not be null");
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(randomSource, "randomSource must not be null");
    Objects.requireNonNull(fileSource, "fileSource must not be null");
    Objects.requireNonNull(manualSource, "manualSource must not be null");
    Objects.requireNonNull(sorterFactory, "sorterFactory must not be null");
    Objects.requireNonNull(paritySorter, "paritySorter must not be null");
    Objects.requireNonNull(resultWriter, "resultWriter must not be null");
    Objects.requireNonNull(resultsPath, "resultsPath must not be null");
    Objects.requireNonNull(occurrenceCounter,
                           "occurrenceCounter must not be null");
  }
}