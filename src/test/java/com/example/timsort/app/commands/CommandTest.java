package com.example.timsort.app.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.example.timsort.app.AppConfig;
import com.example.timsort.app.commands.CountOccurrencesCommand;
import com.example.timsort.app.commands.FillCollectionCommand;
import com.example.timsort.app.commands.ShowCollectionCommand;
import com.example.timsort.app.commands.SortByFieldCommand;
import com.example.timsort.app.commands.WriteResultCommand;
import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.io.ConsoleIO;
import com.example.timsort.io.Session;
import com.example.timsort.model.Bus;
import com.example.timsort.sort.BusField;
import com.example.timsort.sort.TimSorter;
import com.example.timsort.source.DataSource;
import com.example.timsort.source.RandomBusSource;
import com.example.timsort.validation.BusValidator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the individual menu commands: each command is driven
 * with a fake console and a recording writer, without the menu loop.
 *
 * <p>Stub policy (learned the hard way): source stubs never read
 * console input — input is the command's business, canned data is the
 * stub's. Every console read is logged so a broken input script shows
 * its own trace in the failure message.</p>
 */
class CommandTest {

  @TempDir Path tempDir;

  private Session session;
  private RecordingConsole console;
  private RecordingWriter writer;
  private AppConfig config;

  @BeforeEach
  void setUp() {
    session = new Session();
    console = new RecordingConsole();
    writer = new RecordingWriter();
    config = configWith(new ManualBusSourceStub());
  }

  // -------------------------------------------------------------------
  // Config assembly
  // -------------------------------------------------------------------

  /** Assembles the config with one replaceable dependency. */
  private AppConfig configWith(DataSource<Bus> manualSource) {
    return new AppConfig(
        console, session, new RandomBusSource(new BusValidator()),
        new FileBusSourceStub(), manualSource, TimSorter::new,
        new TimParitySorterStub(), writer, tempDir.resolve("results.txt"),
        (data, target) -> data.size()); // OccurrenceCounter stub: list size
  }

  private static Bus bus(int route, String model, long mileage) {
    return Bus.builder()
        .routeNumber(route)
        .model(model)
        .mileage(mileage)
        .build();
  }

  private void fillSession(Bus... buses) {
    CustomArrayList<Bus> list = new CustomArrayList<>();
    list.addAll(Arrays.asList(buses));
    session.setCurrent(list);
  }

  // -------------------------------------------------------------------
  // SortByFieldCommand — one class, three fields (the dedup payoff)
  // -------------------------------------------------------------------

  @Test
  @DisplayName("Sort by ROUTE_NUMBER orders by route")
  void when_sortedByRouteNumber_then_resultIsOrderedByRoute() {
    fillSession(bus(3, "A", 1), bus(1, "B", 2), bus(2, "C", 3));

    new SortByFieldCommand(config, BusField.ROUTE_NUMBER).execute(null);

    List<Bus> sorted = session.getLastResult().orElseThrow();
    assertEquals(List.of(1, 2, 3),
                 sorted.stream().map(Bus::routeNumber).toList());
  }

  @Test
  @DisplayName("Sort by MODEL orders alphabetically")
  void when_sortedByModel_then_resultIsOrderedAlphabetically() {
    fillSession(bus(1, "CCC", 1), bus(2, "AAA", 2), bus(3, "BBB", 3));

    new SortByFieldCommand(config, BusField.MODEL).execute(null);

    assertEquals(List.of("AAA", "BBB", "CCC"), session.getLastResult()
                                                   .orElseThrow()
                                                   .stream()
                                                   .map(Bus::model)
                                                   .toList());
  }

  @Test
  @DisplayName("Sort by MILEAGE orders by mileage")
  void when_sortedByMileage_then_resultIsOrderedByMileage() {
    fillSession(bus(1, "A", 300), bus(2, "B", 100), bus(3, "C", 200));

    new SortByFieldCommand(config, BusField.MILEAGE).execute(null);

    assertEquals(List.of(100L, 200L, 300L), session.getLastResult()
                                                .orElseThrow()
                                                .stream()
                                                .map(Bus::mileage)
                                                .toList());
  }

  @Test
  @DisplayName("Sort does not consume the current collection")
  void when_sorted_then_currentCollectionStaysIntact() {
    fillSession(bus(3, "A", 1), bus(1, "B", 2));
    int sizeBefore = session.getCurrent().orElseThrow().size();

    new SortByFieldCommand(config, BusField.ROUTE_NUMBER).execute(null);

    assertEquals(sizeBefore, session.getCurrent().orElseThrow().size(),
                 "Sorting must not modify the current collection");
  }

  @Test
  @DisplayName("Show reports both slots as unset on a fresh session")
  void when_nothingSet_then_showReportsBothSlotsUnset() {
    // no setCurrent, no setLastResult — the session is fresh

    new ShowCollectionCommand(config).execute(null);

    String out = console.outputText();
    assertTrue(out.contains("Current collection: not set yet."),
               "The current slot must be reported as unset, output: " + out);
    assertTrue(out.contains("Last result: not set yet."),
               "The result slot must be reported as unset, output: " + out);
  }

  // -------------------------------------------------------------------
  // Show / Write / Count / Fill
  // -------------------------------------------------------------------

  @Test
  @DisplayName("Show prints every bus of the collection")
  void when_showCommand_then_everyBusIsPrinted() {
    fillSession(bus(1, "AAA", 10), bus(2, "BBB", 20));

    new ShowCollectionCommand(config).execute(null);

    String out = console.outputText();
    assertTrue(out.contains("Current collection (2 elements)"));
    assertTrue(out.contains("AAA") && out.contains("BBB"),
               "Both models must be printed, output: " + out);
  }

  @Test
  @DisplayName("Write delegates the last result to the writer")
  void when_writeCommand_then_resultGoesThroughWriter() {
    List<Bus> sorted = List.of(bus(1, "A", 1));
    session.setLastResult(sorted);

    new WriteResultCommand(config).execute(null);

    assertEquals(1, writer.calls, "The writer must be used exactly once");
    assertEquals(sorted, writer.lastWritten);
  }

  @Test
  @DisplayName(
      "Write without a previous sort prints the hint and writes nothing")
  void
  when_writeCommandWithoutResult_then_hintAndNoWriting() {
    // no setLastResult — nothing to write

    new WriteResultCommand(config).execute(null);

    assertTrue(console.outputText().contains("No result to write"),
               "The command must explain that sorting comes first");
    assertEquals(0, writer.calls, "No writing may happen");
  }

  @Test
  @DisplayName("Count reports the result of the injected counter")
  void when_countCommand_then_printsInjectedCounterResult() {
    fillSession(bus(1, "A", 1), bus(2, "B", 2));

    // The stub counter returns the collection size (2) regardless of
    // the target — the command must print exactly that value
    console.enqueue("1", "A", "1"); // route, model, mileage of the target

    new CountOccurrencesCommand(config).execute(null);

    assertTrue(
        console.outputText().contains("Occurrences found: 2"),
        "The count result must come from the injected counter, output: " +
            console.outputText());
  }

  @Test
  @DisplayName("Fill delegates to the chosen source and reports the size")
  void when_fillWithManualSource_then_sessionReceivesSourceResult() {
    config = configWith(new ManualBusSourceStub(bus(1, "A", 1)));

    console.enqueue("3", "1"); // source: manual; count: 1

    new FillCollectionCommand(config).execute(null);

    assertEquals(1, session.getCurrent().orElseThrow().size(),
                 "The chosen source must fill the session");
    assertTrue(console.outputText().contains("Collection filled: 1 elements"),
               "The command must report the size, output: " +
                   console.outputText());
  }

  @Test
  @DisplayName("Show prints the last result alongside the current collection")
  void when_showAfterSort_then_resultSlotIsPrinted() {
    fillSession(bus(3, "C", 3), bus(1, "A", 1)); // current: [3, 1] — UNSORTED
    List<Bus> sorted = List.of(bus(1, "A", 1), bus(3, "C", 3));
    session.setLastResult(sorted); // result: [1, 3] — sorted

    new ShowCollectionCommand(config).execute(null);

    String out = console.outputText();
    assertTrue(out.contains("Current collection (2 elements)"),
               "The current slot must be shown, output: " + out);
    assertTrue(out.contains("Last result (2 elements)"),
               "The result slot must be shown, output: " + out);

    // WHY segment-based: the current slot also contains both buses, so
    // global indexOf would match the WRONG block. Compare inside the
    // result segment only.
    String resultSegment = out.substring(out.indexOf("Last result"));
    int pos1 = resultSegment.indexOf("1"); // route of the first sorted bus
    int pos3 = resultSegment.indexOf("3"); // route of the second
    assertTrue(pos1 >= 0 && pos3 > pos1,
               "Inside the result slot the sorted order must be visible");
  }

  // -------------------------------------------------------------------
  // Recording doubles
  // -------------------------------------------------------------------

  /**
   * Fake console: serves queued input, records output, and logs every
   * read — a broken input script then shows its own trace right in the
   * failure message.
   */
  static final class RecordingConsole implements ConsoleIO {
    private final Queue<String> inputs = new LinkedList<>();
    private final List<String> outputs = new ArrayList<>();

    void enqueue(String... lines) { inputs.addAll(Arrays.asList(lines)); }

    @Override
    public String readLine() {
      String line = inputs.poll();
      outputs.add(">> READ: " + line + "\n"); // WHY logged: failure diagnostics
      return line;
    }

    @Override
    public void print(String message) {
      outputs.add(message);
    }

    @Override
    public void println(String message) {
      outputs.add(message + "\n");
    }

    @Override
    public void printf(String format, Object... args) {
      outputs.add(String.format(format, args));
    }

    String outputText() { return String.join("", outputs); }
  }

  /** Records appendAll calls instead of touching the file system. */
  static final class RecordingWriter
      implements com.example.timsort.io.ResultWriter<Bus> {
    int calls;
    List<Bus> lastWritten;

    @Override
    public void appendAll(List<Bus> items) {
      calls++;
      lastWritten = items;
    }
  }

  /**
   * Source stub with canned data. WHY it does not read the console:
   * input is the command's business; a stub that reads couples the
   * test to the exact number of the command's readLine calls.
   */
  static final class ManualBusSourceStub implements DataSource<Bus> {
    private final CustomArrayList<Bus> canned;
    int calls;

    ManualBusSourceStub(Bus... buses) {
      this.canned = new CustomArrayList<>();
      canned.addAll(Arrays.asList(buses));
    }

    @Override
    public CustomArrayList<Bus> provide(int count) {
      calls++;
      CustomArrayList<Bus> result = new CustomArrayList<>();
      if (canned.isEmpty()) {
        return result; // an empty stub yields an empty collection
      }
      for (int i = 0; i < count; i++) {
        result.add(canned.get(i % canned.size()));
      }
      return result;
    }
  }

  /** Source stub serving generated buses. */
  static final class FileBusSourceStub implements DataSource<Bus> {
    int calls;

    @Override
    public CustomArrayList<Bus> provide(int count) {
      calls++;
      CustomArrayList<Bus> list = new CustomArrayList<>();
      for (int i = 0; i < count; i++) {
        list.add(bus(100 + i, "File" + i, i));
      }
      return list;
    }
  }

  /** Parity sorter stub: identity — parity logic is tested elsewhere. */
  static final class TimParitySorterStub
      implements com.example.timsort.sort.ParitySorter<Bus> {
    @Override
    public List<Bus> sort(List<Bus> data) {
      return new ArrayList<>(data);
    }
  }
}