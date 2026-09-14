package com.example.timsort.source;

import com.example.timsort.codec.BusCodec;
import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.ValidationResult;
import com.example.timsort.validation.Validator;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Data source that reads a Bus collection from a CSV file.
 *
 * <p>File format: routeNumber;model;mileage (delimiter {@code ;}).
 * The first line (header) is skipped. Invalid lines are skipped with a
 * warning printed to the output stream.</p>
 */
public class FileBusSource implements DataSource<Bus> {

  private static final int HEADER_LINES_TO_SKIP = 1;
  private static final int FIRST_DATA_LINE_NUMBER = 2;

  private final Path path;
  private final BusCodec codec;
  private final Validator<Bus> validator;
  private final PrintStream output;

  /**
   * Constructor with output to System.out.
   *
   * @param path      path to the CSV file
   * @param codec     codec for decoding lines
   * @param validator validator for checking objects
   */
  public FileBusSource(Path path, BusCodec codec, Validator<Bus> validator) {
    this(path, codec, validator, System.out);
  }

  /**
   * Constructor with a configurable output (for tests).
   *
   * @param path      path to the CSV file
   * @param codec     codec for decoding lines
   * @param validator validator for checking objects
   * @param output    stream for warnings
   * @throws NullPointerException if any parameter is null
   */
  public FileBusSource(Path path, BusCodec codec, Validator<Bus> validator,
                       PrintStream output) {
    this.path = Objects.requireNonNull(path, "path must not be null");
    this.codec = Objects.requireNonNull(codec, "codec must not be null");
    this.validator =
        Objects.requireNonNull(validator, "validator must not be null");
    this.output = Objects.requireNonNull(output, "output must not be null");
  }

  /**
   * Reads the file and returns up to {@code count} valid buses.
   *
   * <p>If the file does not exist or reading fails, an error message is
   * printed and an empty collection is returned. If fewer valid records
   * are found than requested, a warning is printed and all available
   * valid records are returned.</p>
   *
   * @param count the maximum number of records (must be >= 0)
   * @return the valid buses (possibly fewer than count)
   * @throws IllegalArgumentException if count is negative
   */
  @Override
  public CustomArrayList<Bus> provide(int count) {
    if (count < 0) {
      throw new IllegalArgumentException("count must not be negative: " +
                                         count);
    }

    try (Stream<String> lines = Files.lines(path)) {
      AtomicInteger lineNumber = new AtomicInteger(FIRST_DATA_LINE_NUMBER);

      // WHY skip before numbering: the header occupies file line 1, so
      // the first data line is line 2 — the counter starts there.
      // WHY numbering before the blank filter: a blank line occupies a
      // line number in the file; filtering it earlier would shift the
      // numbers of all subsequent lines
      CustomArrayList<Bus> result =
          lines.skip(HEADER_LINES_TO_SKIP)
              .map(line -> Map.entry(lineNumber.getAndIncrement(), line))
              .filter(entry -> !entry.getValue().trim().isEmpty())
              .flatMap(entry
                       -> decodeAndValidate(entry.getValue(), entry.getKey())
                              .stream())
              .limit(count)
              .collect(Collectors.toCollection(CustomArrayList::new));

      if (result.size() < count) {
        output.println("Warning: loaded only " + result.size() + " of " +
                       count + " requested valid records");
      }

      output.flush();
      return result;
    } catch (IOException e) {
      output.println("Failed to read file: " + path + " (" + e.getMessage() +
                     ")");
      output.flush();
      return new CustomArrayList<>();
    }
  }

  /**
   * Decodes a line via BusCodec and validates the result. On a decode
   * or validation failure prints a warning.
   *
   * @param line       the line from the file
   * @param lineNumber the line number (for warnings)
   * @return Optional with a valid Bus, or an empty Optional
   */
  private Optional<Bus> decodeAndValidate(String line, int lineNumber) {
    Optional<Bus> decoded = codec.decode(line);
    if (decoded.isEmpty()) {
      output.println("Line " + lineNumber + " skipped: failed to decode — " +
                     line);
      return Optional.empty();
    }

    Bus bus = decoded.get();
    ValidationResult<Bus> validation = validator.validate(bus);
    if (!validation.isValid()) {
      output.println("Line " + lineNumber +
                     " skipped: " + String.join(", ", validation.errors()));
      return Optional.empty();
    }

    return Optional.of(bus);
  }
}