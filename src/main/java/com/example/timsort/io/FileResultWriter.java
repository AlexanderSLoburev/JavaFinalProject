package com.example.timsort.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A {@link ResultWriter} that appends elements to a file (APPEND mode).
 *
 * <p>Output format:
 * <pre>
 * --- Result from 2025-03-25T12:00:00 (sorted by mileage) ---
 * routeNumber;model;mileage
 * routeNumber;model;mileage
 * ...
 * </pre>
 *
 * <p>The file is created if missing. Repeated {@link #appendAll(List)}
 * calls do not erase previous data: each call appends a new block
 * preceded by a timestamp header (and, when present, the description).
 * An empty list writes a header-only block.</p>
 *
 * <p>WHY human-readable, not round-trip: this file is a terminal output
 * for humans; it is not meant to be fed back into FileBusSource — the
 * block headers of repeated calls would be parsed as data lines.</p>
 *
 * <p>Thread safety: {@code appendAll} is synchronized on this instance,
 * so concurrent calls on one writer never interleave blocks. Distinct
 * writers targeting the same file are NOT coordinated.</p>
 *
 * @param <T> the type of the written elements
 */
public class FileResultWriter<T> implements ResultWriter<T> {

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
  private static final String HEADER_PREFIX = "--- Result from ";
  private static final String HEADER_SUFFIX = " ---";
  private static final String DEFAULT_DESCRIPTION = "";

  private final Path path;
  private final Function<T, String> formatter;
  private final String description;

  /**
   * Creates a writer with an empty description.
   *
   * @param path      path to the result file
   * @param formatter converts an element to its output line; must not
   *        be null and must not return null
   * @throws NullPointerException if {@code path} or {@code formatter}
   *         is null
   */
  public FileResultWriter(Path path, Function<T, String> formatter) {
    this(path, formatter, DEFAULT_DESCRIPTION);
  }

  /**
   * Creates a writer with a processing description.
   *
   * @param path        path to the result file
   * @param formatter   converts an element to its output line; must not
   *        be null and must not return null — a null return is rejected
   *        with a NullPointerException naming the offending item
   * @param description shown in the block header, e.g. "sorted by
   *        mileage"; may be empty or null (treated as empty)
   * @throws NullPointerException if {@code path} or {@code formatter}
   *         is null
   */
  public FileResultWriter(Path path, Function<T, String> formatter,
                          String description) {
    this.path = Objects.requireNonNull(path, "path must not be null");
    this.formatter =
        Objects.requireNonNull(formatter, "formatter must not be null");
    this.description =
        (description == null) ? DEFAULT_DESCRIPTION : description;
  }

  /**
   * Appends a block of data to the end of the file.
   *
   * <p>Each call builds a header with the current timestamp and (when
   * present) the description, then appends the formatted elements.
   * The file is created if missing. An empty list writes a
   * header-only block.</p>
   *
   * @param items the elements to write
   * @throws NullPointerException if {@code items} is null, or if the
   *         formatter returns null for one of the items
   * @throws UncheckedIOException if an I/O error occurs. WHY terminal:
   *         no reasonable retry strategy exists at this level, so the
   *         exception is meant to propagate and stop the caller
   */
  @Override
  public synchronized void appendAll(List<T> items) {
    Objects.requireNonNull(items, "items must not be null");

    // WHY the whole block is built before writing: a failing
    // formatter leaves the file untouched — no orphaned headers,
    // no half-written blocks
    List<String> lines = new ArrayList<>(items.size() + 1);
    lines.add(buildHeader());
    for (T item : items) {
      // WHY the guard: a null line would otherwise surface as an
      // empty-message NPE deep inside java.io.Writer, with no hint
      // that the formatter is the culprit
      String line = formatter.apply(item);
      Objects.requireNonNull(line,
                             () -> "formatter returned null for item: " + item);
      lines.add(line);
    }

    try {
      // WHY explicit WRITE: APPEND implies it, but spelling the trio
      // out keeps the open-mode self-documenting
      Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.WRITE,
                  StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write results to " + path, e);
    }
  }

  /**
   * Builds the header line with the timestamp and description.
   *
   * @return a line like
   *         {@code --- Result from 2025-03-25T12:00:00 (sorted by mileage) ---}
   */
  private String buildHeader() {
    StringBuilder sb = new StringBuilder();
    sb.append(HEADER_PREFIX)
        .append(LocalDateTime.now().format(TIMESTAMP_FORMAT));
    if (!description.isEmpty()) {
      sb.append(" (").append(description).append(')');
    }
    sb.append(HEADER_SUFFIX);
    return sb.toString();
  }
}