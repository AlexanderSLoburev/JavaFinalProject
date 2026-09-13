package com.example.timsort.source;

import static org.junit.jupiter.api.Assertions.*;

import com.example.timsort.app.ConsoleIO;
import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.BusValidator;
import com.example.timsort.validation.Validator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManualBusSourceTest {

  private Validator<Bus> validator;

  @BeforeEach
  void setUp() {
    validator = new BusValidator();
  }

  @Test
  void when_allInputsValid_then_returnsExactCount() {
    // Arrange: two valid buses
    FakeConsoleIO console =
        new FakeConsoleIO("1", "ЛиАЗ-5292", "100000", // bus 1
                          "2", "МАЗ-203", "200000"    // bus 2
        );
    ManualBusSource source = new ManualBusSource(console, validator);

    // Act
    CustomArrayList<Bus> result = source.provide(2);

    // Assert
    assertEquals(2, result.size());
    assertEquals(1, result.get(0).routeNumber());
    assertEquals(2, result.get(1).routeNumber());
    assertFalse(console.getOutputs().stream().anyMatch(
                    out -> out.toLowerCase(Locale.ROOT).contains("error")),
                "No error messages expected for valid input");
  }

  @Test
  void when_invalidNumber_then_retriesAndSucceeds() {
    // Arrange: the first attempt has a non-numeric route number
    FakeConsoleIO console =
        new FakeConsoleIO("abc",                // parse error on routeNumber
                          "42", "ЛиАЗ", "50000" // successful entry
        );
    ManualBusSource source = new ManualBusSource(console, validator);

    // Act
    CustomArrayList<Bus> result = source.provide(1);

    // Assert
    assertEquals(1, result.size());
    assertEquals(42, result.get(0).routeNumber());
    assertTrue(console.getOutputs().stream().anyMatch(
                   out -> out.contains("not a valid number")),
               "A parse error message is expected");
  }

  @Test
  void when_validationFails_then_retriesAndSucceeds() {
    // Arrange: the first attempt has an out-of-range route (0)
    FakeConsoleIO console =
        new FakeConsoleIO("0", "ЛиАЗ", "100000", // validation error (route < 1)
                          "5", "МАЗ", "200000"   // successful entry
        );
    ManualBusSource source = new ManualBusSource(console, validator);

    // Act
    CustomArrayList<Bus> result = source.provide(1);

    // Assert
    assertEquals(1, result.size());
    assertEquals(5, result.get(0).routeNumber());
    assertTrue(console.getOutputs().stream().anyMatch(
                   out -> out.contains("Validation error")),
               "A validation error message is expected");
  }

  @Test
  void when_maxAttemptsExceeded_then_skipsElement() {
    // Arrange: 3 failed attempts for the first element, then a valid bus
    FakeConsoleIO console = new FakeConsoleIO(
        "bad", "bad", "bad",  // 3 parse errors for the first element
        "10", "ПАЗ", "300000" // valid entry for the second element
    );
    ManualBusSource source = new ManualBusSource(console, validator);

    // Act
    CustomArrayList<Bus> result = source.provide(2);

    // Assert
    assertEquals(1, result.size(), "The first element must be skipped");
    assertEquals(10, result.get(0).routeNumber());
    assertTrue(
        console.getOutputs().stream().anyMatch(
            out
            -> out.toLowerCase(Locale.ROOT).contains("attempts exceeded") ||
                   out.toLowerCase(Locale.ROOT).contains("skipped")),
        "An attempts-exceeded message is expected");
  }

  @Test
  void when_inputClosesMidway_then_returnsCollectedSoFar() {
    // Arrange: the first bus is entered, then the input stream closes
    // (the queue is exhausted — readLine() returns null)
    FakeConsoleIO console = new FakeConsoleIO("7", "MAN", "123456");
    ManualBusSource source = new ManualBusSource(console, validator);

    // Act: 3 requested, only 1 available
    CustomArrayList<Bus> result = source.provide(3);

    // Assert
    assertEquals(1, result.size(), "Only the entered bus must be returned");
    assertEquals(7, result.get(0).routeNumber());
    assertTrue(console.getOutputs().stream().anyMatch(
                   out -> out.contains("Input closed")),
               "An input-closed message is expected");
  }

  @Test
  void when_inputClosesBeforeAnyBus_then_returnsEmptyAndStopsImmediately() {
    // Arrange: an empty queue — the very first readLine() is EOF
    FakeConsoleIO console = new FakeConsoleIO();
    ManualBusSource source = new ManualBusSource(console, validator);

    // Act
    CustomArrayList<Bus> result = source.provide(2);

    // Assert
    assertTrue(result.isEmpty());
    // WHY counting prompts: proves the terminal behavior — EOF ends the
    // dialog at once instead of burning 3 x count idle attempts
    long routePrompts = console.getOutputs()
                            .stream()
                            .filter(out -> out.contains("route number"))
                            .count();
    assertEquals(1, routePrompts, "EOF must terminate the dialog immediately");
  }

  @Test
  void when_modelHasSurroundingSpaces_then_trimmedBeforeValidation() {
    // Arrange: the model has leading and trailing spaces
    FakeConsoleIO console = new FakeConsoleIO("3", "  ЛиАЗ-5292  ", "1000");
    ManualBusSource source = new ManualBusSource(console, validator);

    // Act
    CustomArrayList<Bus> result = source.provide(1);

    // Assert
    assertEquals(1, result.size());
    assertEquals("ЛиАЗ-5292", result.get(0).model(),
                 "The model must be trimmed at the UI level");
  }

  @Test
  void when_countIsZero_then_noInteraction() {
    FakeConsoleIO console = new FakeConsoleIO();
    ManualBusSource source = new ManualBusSource(console, validator);

    CustomArrayList<Bus> result = source.provide(0);

    assertTrue(result.isEmpty());
    assertTrue(console.getOutputs().isEmpty(),
               "No prompts are expected for count = 0");
  }

  @Test
  void when_negativeCount_then_throwsException() {
    FakeConsoleIO console = new FakeConsoleIO();
    ManualBusSource source = new ManualBusSource(console, validator);

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> source.provide(-1));
    assertTrue(e.getMessage().contains("negative"),
               "The message must explain the violated contract");
  }

  @Test
  void when_nullConsoleInConstructor_then_throwsNpe() {
    assertThrows(NullPointerException.class,
                 () -> new ManualBusSource(null, validator));
  }

  @Test
  void when_nullValidatorInConstructor_then_throwsNpe() {
    FakeConsoleIO console = new FakeConsoleIO();
    assertThrows(NullPointerException.class,
                 () -> new ManualBusSource(console, null));
  }

  /**
   * A simple fake ConsoleIO for tests: serves lines from a queue and
   * records every output.
   *
   * <p>WHY poll(): returns null when the queue is exhausted — exactly the
   * EOF signal a real stream gives on close.</p>
   */
  private static class FakeConsoleIO implements ConsoleIO {
    private final Queue<String> inputs;
    private final List<String> outputs = new ArrayList<>();

    FakeConsoleIO(String... inputs) {
      this.inputs = new LinkedList<>(Arrays.asList(inputs));
    }

    @Override
    public String readLine() {
      return inputs.poll();
    }

    @Override
    public void print(String message) {
      outputs.add(message);
    }

    @Override
    public void printf(String format, Object... args) {
      outputs.add(String.format(format, args));
    }

    public List<String> getOutputs() { return outputs; }
  }
}