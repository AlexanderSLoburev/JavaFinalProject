package com.example.timsort.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.timsort.io.ConsoleIO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for ConsoleMenu under the exit-flag contract: the loop stops
 * via requestExit() raised by a command or by EOF; invalid input is
 * reported and skipped; registration contracts hold.
 */
class ConsoleMenuTest {

  private FakeConsoleIO io;
  private ConsoleMenu menu;

  @BeforeEach
  void setUp() {
    io = new FakeConsoleIO();
    menu = new ConsoleMenu(io);
  }

  // -------------------------------------------------------------------
  // The happy path
  // -------------------------------------------------------------------

  @Test
  void when_validOptionChosen_then_commandExecutedOnce() {
    AtomicInteger calls = new AtomicInteger();
    menu.register(1, "Do work", m -> calls.incrementAndGet());
    menu.register(0, "Exit", ConsoleMenu::requestExit);

    io.enqueue("1", "0");
    menu.run();

    assertEquals(1, calls.get(), "Command 1 must be executed exactly once");
  }

  @Test
  void when_menuPrinted_then_allRegisteredOptionsAppearInOrder() {
    menu.register(1, "Load data", m -> {});
    menu.register(2, "Sort", m -> {});
    menu.register(0, "Exit", ConsoleMenu::requestExit);

    io.enqueue("0");
    menu.run();

    List<String> out = io.getOutputs();
    int load = indexOfContaining(out, "1. Load data");
    int sort = indexOfContaining(out, "2. Sort");
    int exit = indexOfContaining(out, "0. Exit");
    assertTrue(load >= 0 && sort >= 0 && exit >= 0,
               "All options must be printed");
    assertTrue(load < sort && sort < exit,
               "Options must be printed in registration order");
  }

  // -------------------------------------------------------------------
  // Invalid input
  // -------------------------------------------------------------------

  @Test
  void when_inputIsNotANumber_then_errorPrintedAndLoopContinues() {
    AtomicInteger calls = new AtomicInteger();
    menu.register(1, "Work", m -> calls.incrementAndGet());
    menu.register(0, "Exit", ConsoleMenu::requestExit);

    io.enqueue("abc", "1", "0");
    menu.run();

    assertTrue(
        io.getOutputs().stream().anyMatch(o -> o.contains("enter a number")),
        "A non-number must be reported");
    assertEquals(1, calls.get(), "The loop must continue after the error");
  }

  @Test
  void when_optionIsUnknown_then_errorPrintedAndLoopContinues() {
    AtomicInteger calls = new AtomicInteger();
    menu.register(1, "Work", m -> calls.incrementAndGet());
    menu.register(0, "Exit", ConsoleMenu::requestExit);

    io.enqueue("99", "1", "0");
    menu.run();

    assertTrue(io.getOutputs().stream().anyMatch(
                   o -> o.contains("invalid menu option")),
               "An unknown option must be reported");
    assertEquals(1, calls.get(), "The loop must continue after the error");
  }

  // -------------------------------------------------------------------
  // Exit paths
  // -------------------------------------------------------------------

  @Test
  void when_exitOptionChosen_then_nothingRunsAfterIt() {
    AtomicInteger calls = new AtomicInteger();
    menu.register(1, "Work", m -> calls.incrementAndGet());
    menu.register(0, "Exit", ConsoleMenu::requestExit);

    io.enqueue("0", "1"); // "1" must never be consumed after exit
    menu.run();

    assertEquals(0, calls.get(), "No command must run after the exit option");
    assertEquals(1, iterations(), "Exactly one menu iteration must happen");
  }

  @Test
  void when_commandRequestsExitItself_then_loopStopsAfterIt() {
    AtomicInteger calls = new AtomicInteger();
    menu.register(1, "Work once", m -> {
      calls.incrementAndGet();
      m.requestExit(); // a command may stop the loop itself
    });

    io.enqueue("1", "1");
    menu.run();

    assertEquals(1, calls.get(), "The command must run once");
    assertEquals(1, iterations(), "The loop must stop right after the command");
  }

  @Test
  void when_inputIsExhausted_then_runReturnsQuietly() {
    AtomicInteger calls = new AtomicInteger();
    menu.register(1, "Work", m -> calls.incrementAndGet());

    io.enqueue(); // readLine() -> null immediately (EOF)
    menu.run();   // must simply return: no exception, no hang

    assertEquals(0, calls.get(), "No command may run on EOF");
    assertTrue(io.getOutputs().stream().noneMatch(o -> o.contains("Error")),
               "EOF is not an input error — no message expected");
  }

  // -------------------------------------------------------------------
  // Registration and constructor contracts
  // -------------------------------------------------------------------

  @Test
  void when_sameKeyRegisteredTwice_then_throwsIllegalArgument() {
    menu.register(1, "First", m -> {});
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class,
                     () -> menu.register(1, "Second", m -> {}));
    assertTrue(e.getMessage().contains("already registered"),
               "The message must name the duplicate option");
  }

  @Test
  void when_nullIoInConstructor_then_throwsNpe() {
    assertThrows(NullPointerException.class, () -> new ConsoleMenu(null));
  }

  @Test
  void when_nullDescriptionOrCommandInRegister_then_throwsNpe() {
    assertThrows(NullPointerException.class,
                 () -> menu.register(1, null, m -> {}));
    assertThrows(NullPointerException.class,
                 () -> menu.register(2, "Desc", null));
  }

  // -------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------

  /** One "Choose an option:" prompt per loop iteration. */
  private long iterations() {
    return io.getOutputs()
        .stream()
        .filter(o -> o.contains("Choose an option"))
        .count();
  }

  private static int indexOfContaining(List<String> out, String fragment) {
    for (int i = 0; i < out.size(); i++) {
      if (out.get(i).contains(fragment)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Serves lines from a queue (poll -> null when exhausted, which is
   * exactly the EOF signal) and records every output.
   */
  private static class FakeConsoleIO implements ConsoleIO {
    private final Queue<String> inputs = new LinkedList<>();
    private final List<String> outputs = new ArrayList<>();

    void enqueue(String... lines) { inputs.addAll(Arrays.asList(lines)); }

    @Override
    public String readLine() {
      return inputs.poll();
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
  }
}