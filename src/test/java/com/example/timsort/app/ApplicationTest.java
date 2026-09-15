package com.example.timsort.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.timsort.io.ConsoleIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests for Application.run: full user scenarios driven by a
 * fake console, with real files in a temp directory. No mocks — the
 * wiring itself (source -> session -> sorter -> writer) is under test.
 */
class ApplicationTest {

  @TempDir Path tempDir;

  // -------------------------------------------------------------------
  // Fake console: serves queued input, records output
  // -------------------------------------------------------------------

  static final class FakeConsoleIO implements ConsoleIO {
    private final Queue<String> inputs = new LinkedList<>();
    private final List<String> outputs = new ArrayList<>();

    FakeConsoleIO(String... script) { inputs.addAll(Arrays.asList(script)); }

    @Override
    public String readLine() {
      return inputs.poll(); // null when exhausted == EOF
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

    List<String> getOutputs() { return outputs; }

    String outputText() { return String.join("", outputs); }
  }

  // -------------------------------------------------------------------
  // Full user journeys through run()
  // -------------------------------------------------------------------

  @Test
  @DisplayName("Journey: random fill -> sort by route -> show -> exit")
  void fullJourney_randomFill_sort_show_exit() throws IOException {
    Path results = tempDir.resolve("results.txt");
    FakeConsoleIO console =
        new FakeConsoleIO("1",  // command 1: fill collection
                          "1",  //   source: random
                          "5",  //   elements
                          "3",  // command 3: sort by route number
                          "2",  // command 2: show current collection
                          "0"); // exit

    Application.run(tempDir.resolve("buses.csv"), results, console);

    // The menu terminated (run returned) and the journey left its traces:
    String out = console.outputText();
    assertTrue(out.contains("Collection filled: 5 elements"),
               "The fill command must report the collection size");
    assertTrue(out.contains("Sorted by"),
               "The sort command must confirm the sorting");
    assertTrue(out.contains("Current collection (5 elements)"),
               "The show command must print the collection");
    assertTrue(out.contains("Shutting down"),
               "The exit command must print its farewell");
  }

  @Test
  @DisplayName(
      "Journey: fill from file -> sort by mileage -> write -> file exists")
  void
  fullJourney_fileFill_sort_write() throws IOException {
    Path buses = tempDir.resolve("buses.csv");
    Files.write(buses, List.of("routeNumber;model;mileage", "3;МАЗ-203;300000",
                               "1;ЛиАЗ-5292;100000", "2;ПАЗ-3204;200000"));
    Path results = tempDir.resolve("results.txt");

    FakeConsoleIO console = new FakeConsoleIO(
        "1",  // fill
        "2",  //   source: file
        "10", //   elements (more than the file holds -> warning)
        "5",  // command 5: sort by mileage
        "7",  // command 7: write result
        "0"); // exit

    Application.run(buses, results, console);

    // The results file materialized with the sorted block
    assertTrue(Files.exists(results), "The write command must create the file");
    String content = Files.readString(results, StandardCharsets.UTF_8);
    assertTrue(content.startsWith("--- Result from "),
               "A block header must lead the file");

    // Sorted by mileage: 100000 first, 300000 last
    int pos1 = content.indexOf("1;ЛиАЗ-5292;100000");
    int pos3 = content.indexOf("3;МАЗ-203;300000");
    assertTrue(pos1 >= 0 && pos3 >= 0, "All three buses must be written");
    assertTrue(pos1 < pos3, "Mileage order must be preserved in the output");

    // The source warned about the missing records
    assertTrue(console.outputText().contains("Warning"),
               "Requesting 10 of 3 available must produce a warning");
  }

  @Test
  @DisplayName("Journey: count occurrences of a bus from the collection")
  void fullJourney_countOccurrences() throws IOException {
    Path buses = tempDir.resolve("buses.csv");
    Files.write(buses, List.of("routeNumber;model;mileage", "7;MAN;100",
                               "7;MAN;100", "7;MAN;200"));
    Path results = tempDir.resolve("results.txt");

    FakeConsoleIO console =
        new FakeConsoleIO("1",   // fill
                          "2",   //   source: file
                          "10",  //   elements
                          "8",   // command 8: count occurrences
                          "7",   //   route number
                          "MAN", //   model
                          "100", //   mileage
                          "0");  // exit

    Application.run(buses, results, console);

    // Exactly one of the three buses matches (7;MAN;100 appears twice!)
    assertTrue(console.outputText().contains("Occurrences found: 2"),
               "The counter must find the two identical buses, output: " +
                   console.outputText());
  }

  @Test
  @DisplayName("Guard: every session command refuses an empty collection")
  void sessionCommands_refuseEmptyCollection() {
    Path results = tempDir.resolve("results.txt");
    FakeConsoleIO console =
        new FakeConsoleIO("2", // show — both slots unset
                          "3", // sort — no collection
                          "6", // parity sort — no collection
                          "8", // count — no collection
                          "0");

    Application.run(tempDir.resolve("buses.csv"), results, console);

    String out = console.outputText();

    // Commands 3/6/8 share the AbstractSessionCommand guard
    assertEquals(3,
                 countOccurrences(out, "Collection is empty. Fill it first."),
                 "The sorting/parity/count commands must print the " +
                 "empty-collection hint");

    // Show reports both slots as unset instead of refusing
    assertTrue(out.contains("Current collection: not set yet."),
               "Show must report the unset current slot");
    assertTrue(out.contains("Last result: not set yet."),
               "Show must report the unset last-result slot");
  }

  @Test
  @DisplayName("Guard: write command refuses before any sorting")
  void writeCommand_refusesWithoutResult() {
    Path results = tempDir.resolve("results.txt");
    FakeConsoleIO console = new FakeConsoleIO("7", // write — no last result yet
                                              "0");

    Application.run(tempDir.resolve("buses.csv"), results, console);

    assertTrue(console.outputText().contains("No result to write"),
               "The write command must explain that sorting comes first");
    assertFalse(Files.exists(results),
                "No file may be created when there is nothing to write");
  }

  @Test
  @DisplayName("Invalid menu input is reported and the journey continues")
  void invalidMenuInput_doesNotKillTheJourney() {
    Path results = tempDir.resolve("results.txt");
    FakeConsoleIO console =
        new FakeConsoleIO("abc", // not a number -> error message
                          "99",  // unknown option -> error message
                          "0");

    Application.run(tempDir.resolve("buses.csv"), results, console);

    String out = console.outputText();
    assertTrue(out.contains("enter a number"), "A non-number must be reported");
    assertTrue(out.contains("invalid menu option"),
               "An unknown option must be reported");
    assertTrue(out.contains("Shutting down"),
               "The journey must reach the exit");
  }

  @Test
  @DisplayName("EOF terminates the application quietly")
  void eof_terminatesQuietly() {
    Path results = tempDir.resolve("results.txt");
    FakeConsoleIO console = new FakeConsoleIO(); // no input at all

    Application.run(tempDir.resolve("buses.csv"), results, console);

    // run() returned; nothing crashed, nothing was written
    assertFalse(Files.exists(results));
  }

  // -------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }
}