package com.example.timsort.io;

import static org.junit.jupiter.api.Assertions.*;

import com.example.timsort.codec.BusCodec;
import com.example.timsort.model.Bus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link FileResultWriter}: the APPEND contract of the
 * interface, the block format, robustness (formatter/I-O failures) and
 * the concurrency guarantee declared in the class javadoc.
 */
class FileResultWriterTest {

  @TempDir Path tempDir;

  private Path resultFile;
  private BusCodec codec;
  private FileResultWriter<Bus> writer;

  @BeforeEach
  void setUp() {
    resultFile = tempDir.resolve("result.csv");
    codec = new BusCodec();
    writer = new FileResultWriter<>(resultFile, codec::encode);
  }

  private static Bus bus(int routeNumber, String model, long mileage) {
    return Bus.builder()
        .routeNumber(routeNumber)
        .model(model)
        .mileage(mileage)
        .build();
  }

  // -------------------------------------------------------------------
  // Contract: appending
  // -------------------------------------------------------------------

  @Test
  @DisplayName("The file is created when missing")
  void appendAll_createsFileIfMissing() {
    assertFalse(Files.exists(resultFile),
                "The file must not exist before the call");

    writer.appendAll(List.of());

    assertTrue(Files.exists(resultFile),
               "The file must be created by the first call");
  }

  @Test
  @DisplayName("Repeated calls append instead of overwriting")
  void appendAll_appendsInsteadOfOverwriting() throws IOException {
    Bus bus1 = bus(1, "ПАЗ-3205", 10_000);
    Bus bus2 = bus(2, "ГАЗель Next", 20_000);

    writer.appendAll(List.of(bus1));
    writer.appendAll(List.of(bus2));

    String content = Files.readString(resultFile, StandardCharsets.UTF_8);
    assertTrue(content.contains(codec.encode(bus1)),
               "The first block must survive");
    assertTrue(content.contains(codec.encode(bus2)),
               "The second block must be appended");

    // Both headers present => two appendAll calls
    long headerCount = content.lines()
                           .filter(line -> line.startsWith("--- Result from "))
                           .count();
    assertEquals(2, headerCount,
                 "There must be two headers — one per appendAll call");

    // "Append" also means: to the end, after the previous block
    int firstBlock = content.indexOf(codec.encode(bus1));
    int secondBlock = content.indexOf(codec.encode(bus2));
    assertTrue(firstBlock >= 0 && secondBlock > firstBlock,
               "The second block must come after the first one");
  }

  @Test
  @DisplayName("Pre-existing file content survives (true APPEND mode)")
  void appendAll_appendsToPreexistingFile() throws IOException {
    Files.writeString(resultFile, "PREEXISTING LINE\n", StandardCharsets.UTF_8);

    writer.appendAll(List.of(bus(1, "ПАЗ-3205", 10_000)));

    String content = Files.readString(resultFile, StandardCharsets.UTF_8);
    assertTrue(content.startsWith("PREEXISTING LINE"),
               "The pre-existing content must not be erased");
    assertTrue(content.contains(codec.encode(bus(1, "ПАЗ-3205", 10_000))),
               "The new block must be appended after it");
  }

  @Test
  @DisplayName("Element order is preserved")
  void appendAll_preservesOrder() throws IOException {
    Bus bus1 = bus(1, "A", 1);
    Bus bus2 = bus(2, "B", 2);
    Bus bus3 = bus(3, "C", 3);

    writer.appendAll(List.of(bus1, bus2, bus3));

    List<String> lines = Files.readAllLines(resultFile, StandardCharsets.UTF_8);
    int i1 = lines.indexOf(codec.encode(bus1));
    int i2 = lines.indexOf(codec.encode(bus2));
    int i3 = lines.indexOf(codec.encode(bus3));

    assertTrue(i1 >= 0 && i2 >= 0 && i3 >= 0,
               "All formatted lines must be found (a -1 means 'not found', "
                   + "not 'wrong order')");
    assertTrue(i1 < i2 && i2 < i3, "The element order must be preserved");
  }

  // -------------------------------------------------------------------
  // Block format
  // -------------------------------------------------------------------

  @Test
  @DisplayName("Each element is formatted by the injected function")
  void appendAll_formatsEachElement() throws IOException {
    Bus bus1 = bus(42, "ЛиАЗ-5292", 150_000);
    Bus bus2 = bus(7, "МАЗ-203", 80_000);

    writer.appendAll(List.of(bus1, bus2));

    String content = Files.readString(resultFile, StandardCharsets.UTF_8);
    assertTrue(content.contains(codec.encode(bus1)),
               "The content must contain the formatted bus1");
    assertTrue(content.contains(codec.encode(bus2)),
               "The content must contain the formatted bus2");
  }

  @Test
  @DisplayName("A header with a timestamp precedes the data block")
  void appendAll_writesHeaderWithTimestamp() throws IOException {
    writer.appendAll(List.of());

    String header =
        Files.readAllLines(resultFile, StandardCharsets.UTF_8).get(0);
    assertTrue(header.startsWith("--- Result from "),
               "The file must start with a header");
    assertTrue(header.endsWith(" ---"), "The header must end with ' ---'");
    assertTrue(
        header.matches(
            "--- Result from \\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"),
        "The header must contain an ISO-like timestamp");
  }

  @Test
  @DisplayName("The description is included in the header when provided")
  void appendAll_includesDescriptionWhenProvided() throws IOException {
    FileResultWriter<Bus> describedWriter =
        new FileResultWriter<>(resultFile, codec::encode, "sorted by mileage");

    describedWriter.appendAll(List.of());

    String content = Files.readString(resultFile, StandardCharsets.UTF_8);
    assertTrue(content.contains("(sorted by mileage)"),
               "The description must appear in the header");
  }

  @Test
  @DisplayName("An empty list writes a header-only block")
  void appendAll_emptyListWritesOnlyHeader() throws IOException {
    writer.appendAll(List.of());

    List<String> lines = Files.readAllLines(resultFile, StandardCharsets.UTF_8);
    assertEquals(1, lines.size(), "There must be only the header line");
    assertTrue(lines.get(0).startsWith("--- Result from "));
  }

  // -------------------------------------------------------------------
  // Robustness
  // -------------------------------------------------------------------

  @Test
  @DisplayName("A failing formatter leaves the file untouched")
  void appendAll_formatterThrows_fileUntouched() {
    FileResultWriter<Bus> failing = new FileResultWriter<>(
        resultFile, b -> { throw new IllegalStateException("boom"); });

    assertThrows(IllegalStateException.class,
                 () -> failing.appendAll(List.of(bus(1, "X", 1))));
    assertFalse(Files.exists(resultFile),
                "The file must stay untouched when formatting fails");
  }

  @Test
  @DisplayName("A null-returning formatter is rejected with a clear NPE")
  void appendAll_formatterReturnsNull_throwsNpeNamingTheCulprit() {
    FileResultWriter<Bus> nulling =
        new FileResultWriter<>(resultFile, b -> null);

    NullPointerException e =
        assertThrows(NullPointerException.class,
                     () -> nulling.appendAll(List.of(bus(1, "X", 1))));

    assertTrue(e.getMessage().contains("formatter"),
               "The message must point at the formatter, got: " +
                   e.getMessage());
    assertFalse(Files.exists(resultFile),
                "The file must stay untouched when formatting fails");
  }

  @Test
  @DisplayName("An I/O failure is wrapped in UncheckedIOException")
  void appendAll_ioFailure_throwsUncheckedIOException() {
    // path is a directory — Files.write always fails here
    FileResultWriter<Bus> badWriter =
        new FileResultWriter<>(tempDir, codec::encode);

    UncheckedIOException e = assertThrows(UncheckedIOException.class,
                                          () -> badWriter.appendAll(List.of()));
    assertNotNull(e.getCause(), "The original IOException must be preserved");
    assertTrue(e.getMessage().contains("Failed to write results"),
               "The message must point at the failing path");
  }

  // -------------------------------------------------------------------
  // Concurrency (declared in the class javadoc)
  // -------------------------------------------------------------------

  @Test
  @DisplayName("Concurrent calls never interleave blocks")
  void appendAll_concurrentCalls_neverInterleaveBlocks() throws Exception {
    int blocks = 8;
    FileResultWriter<Bus> shared =
        new FileResultWriter<>(resultFile, b -> "marker-" + b.routeNumber());

    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Callable<Void>> tasks = new ArrayList<>();
      for (int t = 0; t < blocks; t++) {
        int id = t;
        tasks.add(() -> {
          shared.appendAll(List.of(bus(id, "X", id)));
          return null;
        });
      }
      pool.invokeAll(tasks);
    } finally {
      pool.shutdown();
      assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    List<String> lines = Files.readAllLines(resultFile, StandardCharsets.UTF_8);
    assertEquals(2 * blocks, lines.size(),
                 "8 blocks of [header + one data line] each");

    long headers = lines.stream()
                       .filter(line -> line.startsWith("--- Result from "))
                       .count();
    assertEquals(blocks, headers, "One header per appendAll call");

    // WHY the pairwise check: a block is intact exactly when every
    // header is immediately followed by its data line — interleaved
    // writes would break this pairing
    Set<String> markers = new HashSet<>();
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).startsWith("--- Result from ")) {
        String data = lines.get(i + 1);
        assertTrue(data.startsWith("marker-"),
                   "A header must be followed by its data line, got: " + data);
        markers.add(data);
      }
    }
    assertEquals(blocks, markers.size(),
                 "Each task must contribute its own marker");
  }

  // -------------------------------------------------------------------
  // Constructor and argument contracts
  // -------------------------------------------------------------------

  @Test
  @DisplayName("appendAll(null) throws NullPointerException")
  void appendAll_nullList_throwsException() {
    assertThrows(NullPointerException.class, () -> writer.appendAll(null));
  }

  @Test
  @DisplayName("Null path/formatter in the constructor throw")
  void constructor_nullArguments_throwException() {
    assertThrows(NullPointerException.class,
                 () -> new FileResultWriter<Bus>(null, codec::encode));
    assertThrows(NullPointerException.class,
                 () -> new FileResultWriter<Bus>(resultFile, null));
  }

  @Test
  @DisplayName("A null description is treated as empty")
  void constructor_nullDescription_treatedAsEmpty() throws IOException {
    FileResultWriter<Bus> nullDescriptionWriter =
        new FileResultWriter<>(resultFile, codec::encode, null);

    nullDescriptionWriter.appendAll(List.of());

    String header =
        Files.readAllLines(resultFile, StandardCharsets.UTF_8).get(0);
    assertTrue(header.endsWith(" ---"));
    assertFalse(header.contains(" ("),
                "A null description must produce no parentheses");
  }
}